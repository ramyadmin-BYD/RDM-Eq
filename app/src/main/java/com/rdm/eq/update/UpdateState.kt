package com.rdm.eq.update

import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val downloaded: Long, val total: Long) : UpdateState {
        val percent: Int get() = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
    }
    data class Verifying(val update: AvailableUpdate) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate, val file: File) : UpdateState
    /** Android needs the user to allow "install unknown apps" for RDM Eq first. */
    data class NeedsInstallPermission(val update: AvailableUpdate, val file: File) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String, val kind: UpdateException.Kind, val update: AvailableUpdate? = null) : UpdateState
}

/** Short text for the About / Settings screens. */
fun UpdateState.statusText(): String = when (this) {
    UpdateState.Idle -> "Not checked yet"
    UpdateState.Checking -> "Checking for updates…"
    is UpdateState.UpToDate -> "You're using the latest version of RDM Eq."
    is UpdateState.Available -> "Version ${update.metadata.versionName} is available"
    is UpdateState.Downloading -> "Downloading ${update.metadata.versionName}… $percent %"
    is UpdateState.Verifying -> "Verifying download…"
    is UpdateState.ReadyToInstall -> "Version ${update.metadata.versionName} downloaded and verified — ready to install"
    is UpdateState.NeedsInstallPermission -> "Allow RDM Eq to install updates to continue"
    is UpdateState.Installing -> "Waiting for Android's installer…"
    is UpdateState.Failed -> message
}
