package com.whatstats.update

sealed interface ForceUpdateState {
    data object Idle : ForceUpdateState

    data class Required(val customMessage: String) : ForceUpdateState
}
