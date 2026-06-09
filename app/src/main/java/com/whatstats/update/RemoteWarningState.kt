package com.whatstats.update

enum class RemoteWarningAction {
    DISMISS,
    OPEN_URL,
    OPEN_PLAY_STORE,
}

data class RemoteWarningPayload(
    val title: String,
    val message: String,
    val primaryButtonLabel: String,
    val secondaryButtonLabel: String,
    val primaryAction: RemoteWarningAction,
    val secondaryAction: RemoteWarningAction,
    val actionUrl: String,
)

sealed interface RemoteWarningState {
    data object Hidden : RemoteWarningState

    data class Visible(val payload: RemoteWarningPayload) : RemoteWarningState
}
