package com.rdm.eq.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.rdm.eq.BuildConfig
import com.rdm.eq.util.DiagLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * In-app updates from GitHub Releases:
 * check → download (resumable) → verify (size, SHA-256, package name, version, signing certificate)
 * → hand the APK to Android's installer, which always asks the user to confirm.
 *
 * Network work runs on a background thread and never touches audio focus, so playback continues.
 */
class UpdateManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("rdm_eq_update", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "updates").apply { mkdirs() }
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    // "Latest version" is only shown if it was confirmed for the version that is installed now.
    private val _state = MutableStateFlow<UpdateState>(
        prefs.getLong(KEY_LAST_OK, 0).takeIf { it > 0 && prefs.getLong(KEY_LAST_OK_CODE, -1) == versionCodeOf(packageInfo()) }
            ?.let { UpdateState.UpToDate(it) } ?: UpdateState.Idle,
    )
    val state: StateFlow<UpdateState> = _state

    /** True while the update dialog should be on screen. */
    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck
    private val _beta = MutableStateFlow(prefs.getBoolean(KEY_BETA, false))
    val includeBeta: StateFlow<Boolean> = _beta

    val installedVersionName: String get() = packageInfo().versionName ?: "?"
    val installedVersionCode: Long get() = versionCodeOf(packageInfo())
    val lastCheckedAt: Long get() = prefs.getLong(KEY_LAST_CHECK, 0)

    fun setAutoCheck(on: Boolean) { prefs.edit().putBoolean(KEY_AUTO, on).apply(); _autoCheck.value = on }
    fun setIncludeBeta(on: Boolean) { prefs.edit().putBoolean(KEY_BETA, on).apply(); _beta.value = on }

    // ---- checking ---------------------------------------------------------------------------

    /** Called when the app comes to the foreground; checks at most once per [AUTO_INTERVAL_MS]. */
    fun maybeAutoCheck() {
        if (!_autoCheck.value) return
        if (System.currentTimeMillis() - lastCheckedAt < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        // While Android's installer is open, don't change the state under it.
        if (_state.value is UpdateState.Installing) {
            if (manual) _dialog.value = true
            return
        }
        // A file downloaded earlier is re-verified below against the latest metadata, so a
        // replaced or newer release is never installed from a stale download.
        checkJob = scope.launch {
            _state.value = UpdateState.Checking
            try {
                val update = withContext(Dispatchers.IO) { findUpdate() }
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (update == null) {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_OK, now).putLong(KEY_LAST_OK_CODE, installedVersionCode).apply()
                    _state.value = UpdateState.UpToDate(now)
                    DiagLog.i("Update check: up to date (${installedVersionName})")
                    if (manual) _dialog.value = true
                } else {
                    _state.value = UpdateState.Available(update)
                    DiagLog.i("Update check: ${update.metadata.versionName} (${update.metadata.versionCode}) available")
                    // Downloaded earlier and still intact? Skip straight to install.
                    finishedFile(update).takeIf { it.exists() }?.let { f ->
                        runCatching { withContext(Dispatchers.IO) { verify(update, f) } }
                            .onSuccess { _state.value = UpdateState.ReadyToInstall(update, f) }
                            .onFailure { f.delete() }
                    }
                    if (manual || !isSnoozed(update.metadata.versionCode)) _dialog.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ue = e.toUpdateException()
                DiagLog.w("Update check failed: ${ue.message}")
                _state.value = UpdateState.Failed(ue.message ?: "Update check failed", ue.kind)
                if (manual) _dialog.value = true
            }
        }
    }

    private fun findUpdate(): AvailableUpdate? {
        val releases = ReleaseParser.parseReleases(httpGetText("${apiBase()}/repos/${repo()}/releases?per_page=15", api = true))
        val candidates = ReleaseParser.candidates(releases, _beta.value, BuildConfig.UPDATE_METADATA_ASSET)
        val certs = installedCertDigests()
        var lastError: UpdateException? = null
        // Newest eligible release first; a broken newest release must not hide a valid older one.
        for (r in candidates) {
            try {
                val metaAsset = r.assets.first { it.name == BuildConfig.UPDATE_METADATA_ASSET }
                val meta = ReleaseParser.parseMetadata(httpGetText(metaAsset.url, api = false))
                return ReleaseParser.validate(r, meta, context.packageName, installedVersionCode, certs, Build.VERSION.SDK_INT)
                    ?: return null // newest valid release is not newer: up to date
            } catch (e: UpdateException) {
                if (e.kind == UpdateException.Kind.NETWORK || e.kind == UpdateException.Kind.RATE_LIMIT) throw e
                DiagLog.w("Skipping release ${r.tag}: ${e.message}")
                lastError = e
            }
        }
        if (lastError != null) throw lastError
        return null
    }

    // ---- download & verify -------------------------------------------------------------------

    fun download() {
        val update = currentUpdate() ?: return
        if (downloadJob?.isActive == true) return // never two downloads at once
        downloadJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadTo(update) }
                _state.value = UpdateState.Verifying(update)
                withContext(Dispatchers.IO) { verify(update, file) }
                _state.value = UpdateState.ReadyToInstall(update, file)
                DiagLog.i("Update ${update.metadata.versionName} downloaded and verified")
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(update) // partial file kept for resume
                throw e
            } catch (e: Exception) {
                val ue = e.toUpdateException()
                DiagLog.w("Update download failed: ${ue.message}")
                _state.value = UpdateState.Failed(ue.message ?: "Download failed", ue.kind, update)
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    private suspend fun downloadTo(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val meta = update.metadata
        val done = finishedFile(update)
        val part = File(dir, done.name + ".part")
        dir.listFiles()?.filter { it != part && it != done }?.forEach { it.delete() } // old versions
        if (done.exists()) return@withContext done
        if (meta.size > 0 && part.length() > meta.size) part.delete()
        val start = part.length()
        val conn = open(update.apk.url, api = false).apply {
            if (start > 0) setRequestProperty("Range", "bytes=$start-")
        }
        try {
            val code = conn.responseCode
            val resuming = start > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resuming) throw httpError(code, conn)
            if (!resuming && start > 0) part.delete() // server ignored Range: start over
            val total = if (meta.size > 0) meta.size else (conn.contentLengthLong.takeIf { it > 0 }?.plus(if (resuming) start else 0) ?: -1)
            var done0 = if (resuming) start else 0L
            _state.value = UpdateState.Downloading(update, done0, total)
            conn.inputStream.use { input ->
                FileOutputStream(part, resuming).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done0 += n
                        if (meta.size > 0 && done0 > meta.size) throw UpdateException("Download is larger than expected", UpdateException.Kind.VERIFY)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150) { _state.value = UpdateState.Downloading(update, done0, total); lastEmit = now }
                    }
                }
            }
            _state.value = UpdateState.Downloading(update, done0, total)
            if (meta.size > 0 && part.length() != meta.size) throw UpdateException("Download was interrupted — tap Retry to resume", UpdateException.Kind.NETWORK)
            if (!part.renameTo(done)) throw IOException("Could not save the update file")
            done
        } finally {
            conn.disconnect()
        }
    }

    /** Throws unless the file is exactly the APK the release metadata describes, signed like this app. */
    private fun verify(update: AvailableUpdate, file: File) {
        val meta = update.metadata
        fun fail(msg: String): Nothing { file.delete(); throw UpdateException(msg, UpdateException.Kind.VERIFY) }
        if (meta.size > 0 && file.length() != meta.size) fail("Downloaded file has the wrong size")
        val sha = sha256(file)
        if (sha != meta.sha256) fail("Checksum mismatch — the download is corrupted or was altered")
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
            ?: fail("Downloaded file is not a valid Android package")
        if (info.packageName != context.packageName) fail("Downloaded package is ${info.packageName}, not RDM Eq")
        if (versionCodeOf(info) != meta.versionCode.toLong()) fail("Downloaded package version does not match the release")
        if (versionCodeOf(info) <= installedVersionCode) fail("Downloaded package is not newer than the installed app")
        val apkCerts = certDigests(info)
        if (apkCerts.isEmpty()) fail("Could not read the update's signing certificate")
        if (apkCerts.intersect(installedCertDigests()).isEmpty()) fail("Update is signed with a different key — refusing to install")
    }

    // ---- install -----------------------------------------------------------------------------

    /** Starts installation; Android shows its own confirmation screen. Never silent, never uninstalls. */
    fun install() {
        val (update, file) = when (val s = _state.value) {
            is UpdateState.ReadyToInstall -> s.update to s.file
            is UpdateState.NeedsInstallPermission -> s.update to s.file
            is UpdateState.Failed -> (s.update ?: return) to finishedFile(s.update).takeIf { it.exists() }.let { it ?: return }
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(update, file)
            return
        }
        _state.value = UpdateState.Installing(update)
        try {
            installWithSession(file)
            DiagLog.i("Update ${update.metadata.versionName}: install session committed")
        } catch (e: Exception) {
            DiagLog.w("PackageInstaller session failed, falling back to system installer", e)
            try { installWithIntent(file) } catch (e2: Exception) {
                _state.value = UpdateState.Failed("Android's installer could not be opened on this unit: ${e2.message}", UpdateException.Kind.INSTALL, update)
            }
        }
    }

    /** Opens Android's "Install unknown apps" switch for RDM Eq. Returns false if the head unit hides it. */
    fun openInstallPermissionSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ); true
    } catch (e: ActivityNotFoundException) { false }

    /** Call on resume: continues automatically once the user has allowed installs. */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.NeedsInstallPermission && context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.ReadyToInstall(s.update, s.file)
            install()
        } else if (s is UpdateState.Installing) {
            // User backed out of the system installer: offer the button again.
            scope.launch {
                kotlinx.coroutines.delay(1500)
                val now = _state.value
                if (now is UpdateState.Installing) finishedFile(now.update).takeIf { it.exists() }?.let {
                    _state.value = UpdateState.ReadyToInstall(now.update, it)
                }
            }
        }
    }

    private fun installWithSession(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("rdm-eq.apk", 0, file.length()).use { out ->
                    input.copyTo(out); session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(pi.intentSender)
        }
    }

    private fun installWithIntent(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true),
        )
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val update = currentUpdate() ?: return
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> DiagLog.i("Update installed")
            PackageInstaller.STATUS_FAILURE_ABORTED -> when {
                // Blocked by the device's security scanner (e.g. Google Play Protect), not by the user.
                message?.contains("VERIFICATION", ignoreCase = true) == true -> _state.value = UpdateState.Failed(
                    "This device's security check (for example Google Play Protect) blocked the update. " +
                        "Your current version keeps working. You can install it from github.com/${BuildConfig.UPDATE_REPO}/releases instead.",
                    UpdateException.Kind.INSTALL, update,
                )
                else -> finishedFile(update).takeIf { it.exists() }?.let { _state.value = UpdateState.ReadyToInstall(update, it) }
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> _state.value = UpdateState.Failed(
                "This head unit does not allow apps to install updates. Install the APK from github.com/${BuildConfig.UPDATE_REPO}/releases via USB instead.",
                UpdateException.Kind.INSTALL, update,
            )
            else -> _state.value = UpdateState.Failed(
                "Installation failed${message?.let { ": $it" } ?: ""}", UpdateException.Kind.INSTALL, update,
            )
        }
    }

    // ---- dialog / snooze ------------------------------------------------------------------------

    fun showDialog() { _dialog.value = true }

    /** "Later": hide the dialog; automatic checks stay quiet about this version for a day. */
    fun later() {
        currentUpdate()?.let { prefs.edit().putInt(KEY_SNOOZE_CODE, it.metadata.versionCode).putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS).apply() }
        _dialog.value = false
    }

    fun dismiss() { _dialog.value = false }

    private fun isSnoozed(code: Int) =
        prefs.getInt(KEY_SNOOZE_CODE, -1) == code && System.currentTimeMillis() < prefs.getLong(KEY_SNOOZE_UNTIL, 0)

    // ---- helpers ------------------------------------------------------------------------------

    fun currentUpdate(): AvailableUpdate? = when (val s = _state.value) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Verifying -> s.update
        is UpdateState.ReadyToInstall -> s.update
        is UpdateState.NeedsInstallPermission -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    }

    private fun finishedFile(u: AvailableUpdate) = File(dir, "RDM-Eq-${u.metadata.versionCode}.apk")

    private fun apiBase(): String =
        (if (BuildConfig.UPDATE_ALLOW_TEST_SOURCE) prefs.getString(KEY_TEST_API, null) else null) ?: BuildConfig.UPDATE_API_BASE

    private fun repo(): String =
        (if (BuildConfig.UPDATE_ALLOW_TEST_SOURCE) prefs.getString(KEY_TEST_REPO, null) else null) ?: BuildConfig.UPDATE_REPO

    /** Debug builds only: point the updater at a local test server. */
    fun setTestSource(apiBase: String?, repo: String?) {
        if (!BuildConfig.UPDATE_ALLOW_TEST_SOURCE) return
        prefs.edit().putString(KEY_TEST_API, apiBase).putString(KEY_TEST_REPO, repo).putLong(KEY_LAST_CHECK, 0).apply()
    }

    private fun open(url: String, api: Boolean): HttpURLConnection {
        val u = URL(url)
        if (u.protocol != "https" && !BuildConfig.UPDATE_ALLOW_TEST_SOURCE) throw UpdateException("Refusing non-HTTPS update URL")
        return (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            instanceFollowRedirects = true // GitHub serves assets via a redirect to its CDN
            setRequestProperty("User-Agent", "RDM-Eq-Updater/${installedVersionName}")
            if (api) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            } else setRequestProperty("Accept", "application/octet-stream")
        }
    }

    private fun httpGetText(url: String, api: Boolean): String {
        val c = open(url, api)
        try {
            val code = c.responseCode
            if (code != 200) throw httpError(code, c)
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > 2_000_000) throw UpdateException("Update information is unexpectedly large")
            return bytes.decodeToString()
        } finally { c.disconnect() }
    }

    private fun httpError(code: Int, c: HttpURLConnection): UpdateException = when {
        (code == 403 || code == 429) && c.getHeaderField("X-RateLimit-Remaining") == "0" ->
            UpdateException("GitHub's hourly limit for update checks was reached. Try again later.", UpdateException.Kind.RATE_LIMIT)
        code == 404 -> UpdateException("No releases found on GitHub yet", UpdateException.Kind.INVALID)
        code >= 500 -> UpdateException("GitHub is having problems (HTTP $code). Try again later.", UpdateException.Kind.NETWORK)
        else -> UpdateException("GitHub answered HTTP $code", UpdateException.Kind.NETWORK)
    }

    private fun Exception.toUpdateException(): UpdateException = when (this) {
        is UpdateException -> this
        is UnknownHostException -> UpdateException("No internet connection. Check the car's Wi-Fi or mobile data and try again.", UpdateException.Kind.NETWORK)
        is SocketTimeoutException -> UpdateException("The connection timed out. Try again.", UpdateException.Kind.NETWORK)
        is IOException -> UpdateException("Network error: ${message ?: javaClass.simpleName}", UpdateException.Kind.NETWORK)
        else -> UpdateException("Unexpected error: ${message ?: javaClass.simpleName}", UpdateException.Kind.INVALID)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)

    private fun installedCertDigests(): Set<String> = certDigests(packageInfo())

    companion object {
        const val AUTO_INTERVAL_MS = 12 * 60 * 60 * 1000L
        const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        private const val KEY_AUTO = "auto_check"
        private const val KEY_BETA = "include_beta"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_OK = "last_up_to_date"
        private const val KEY_LAST_OK_CODE = "last_up_to_date_code"
        private const val KEY_SNOOZE_CODE = "snooze_code"
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
        private const val KEY_TEST_API = "test_api_base"
        private const val KEY_TEST_REPO = "test_repo"

        @Suppress("DEPRECATION")
        fun versionCodeOf(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

        @Suppress("DEPRECATION")
        fun certDigests(p: PackageInfo): Set<String> {
            val sigs = p.signingInfo?.let { si ->
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } ?: p.signatures
            return sigs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
