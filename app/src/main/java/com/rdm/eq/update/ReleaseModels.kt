package com.rdm.eq.update

import org.json.JSONArray
import org.json.JSONObject

/** One downloadable file attached to a GitHub release. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    /** GitHub's own checksum ("sha256:<hex>") when GitHub provides it, lowercase hex only. */
    val githubSha256: String?,
)

/** The parts of a GitHub release the updater needs. */
data class GitHubRelease(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

/**
 * `rdm-eq-update.json`, written by scripts/release.sh and attached to every release.
 * It ties a release to one exact APK file, checksum and signing certificate.
 */
data class UpdateMetadata(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val sha256: String,
    val size: Long,
    val signingCertSha256: String,
    val minSdk: Int,
)

/** A release that passed every check and is newer than the installed app. */
data class AvailableUpdate(
    val release: GitHubRelease,
    val metadata: UpdateMetadata,
    val apk: ReleaseAsset,
)

class UpdateException(message: String, val kind: Kind = Kind.INVALID) : Exception(message) {
    enum class Kind { NETWORK, INVALID, VERIFY, INSTALL, RATE_LIMIT }
}

/** Parsing and selection logic. Pure Kotlin + org.json so it is covered by JVM unit tests. */
object ReleaseParser {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    fun parseReleases(json: String): List<GitHubRelease> {
        val arr = try { JSONArray(json) } catch (e: Exception) {
            throw UpdateException("GitHub returned an unexpected response")
        }
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseRelease) }
    }

    private fun parseRelease(o: JSONObject): GitHubRelease? {
        val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val assets = o.optJSONArray("assets")?.let { a ->
            (0 until a.length()).mapNotNull { j ->
                val x = a.optJSONObject(j) ?: return@mapNotNull null
                val name = x.optString("name"); val url = x.optString("browser_download_url")
                if (name.isBlank() || url.isBlank()) return@mapNotNull null
                val digest = x.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
                ReleaseAsset(name, url, x.optLong("size", -1), digest?.takeIf { HEX64.matches(it) })
            }
        }.orEmpty()
        return GitHubRelease(
            tag = tag,
            name = o.optString("name").ifBlank { tag },
            notes = o.optString("body"),
            publishedAt = o.optString("published_at"),
            draft = o.optBoolean("draft", false),
            prerelease = o.optBoolean("prerelease", false),
            assets = assets,
        )
    }

    /** Newest-first candidates: never drafts, prereleases only when beta updates are on, and must carry metadata. */
    fun candidates(releases: List<GitHubRelease>, includeBeta: Boolean, metadataAsset: String): List<GitHubRelease> =
        releases.filter { !it.draft && (includeBeta || !it.prerelease) && it.assets.any { a -> a.name == metadataAsset } }

    fun parseMetadata(json: String): UpdateMetadata {
        val o = try { JSONObject(json) } catch (e: Exception) { throw UpdateException("Update information is unreadable") }
        fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() } ?: throw UpdateException("Update information is missing \"$k\"")
        val sha = str("sha256").lowercase()
        val cert = str("signingCertSha256").lowercase().replace(":", "")
        if (!HEX64.matches(sha)) throw UpdateException("Update checksum is malformed")
        if (!HEX64.matches(cert)) throw UpdateException("Update signing certificate is malformed")
        val code = o.optInt("versionCode", -1).takeIf { it > 0 } ?: throw UpdateException("Update version code is missing")
        val apkName = str("apk")
        if (!apkName.endsWith(".apk") || apkName.contains('/')) throw UpdateException("Update file name is invalid")
        return UpdateMetadata(
            applicationId = str("applicationId"),
            versionCode = code,
            versionName = str("versionName"),
            apkName = apkName,
            sha256 = sha,
            size = o.optLong("size", -1),
            signingCertSha256 = cert,
            minSdk = o.optInt("minSdk", 1),
        )
    }

    /**
     * Checks metadata against the installed app and the release's assets, and returns the APK asset to download.
     * Returns null when the release is not newer (that is not an error).
     */
    fun validate(
        release: GitHubRelease,
        meta: UpdateMetadata,
        installedPackage: String,
        installedVersionCode: Long,
        installedCertSha256: Set<String>,
        deviceSdk: Int,
    ): AvailableUpdate? {
        if (meta.applicationId != installedPackage) throw UpdateException("Update is for a different app (${meta.applicationId})")
        if (meta.versionCode <= installedVersionCode) return null
        if (meta.minSdk > deviceSdk) throw UpdateException("Update ${meta.versionName} needs Android API ${meta.minSdk}; this unit has $deviceSdk")
        if (installedCertSha256.isNotEmpty() && meta.signingCertSha256 !in installedCertSha256)
            throw UpdateException("Update is signed with a different key than the installed app", UpdateException.Kind.VERIFY)
        val apk = release.assets.firstOrNull { it.name == meta.apkName }
            ?: throw UpdateException("Release ${release.tag} has no file named ${meta.apkName}")
        if (meta.size > 0 && apk.size > 0 && meta.size != apk.size) throw UpdateException("Update file size does not match its metadata")
        if (apk.githubSha256 != null && apk.githubSha256 != meta.sha256)
            throw UpdateException("Update checksum does not match GitHub's record", UpdateException.Kind.VERIFY)
        return AvailableUpdate(release, meta, apk)
    }

    /** "2026-11-05T10:12:00Z" → "5 Nov 2026"; falls back to the raw text. */
    fun formatDate(iso: String): String = runCatching {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val (y, m, d) = iso.substring(0, 10).split("-").map { it.toInt() }
        "$d ${months[m - 1]} $y"
    }.getOrDefault(iso)
}
