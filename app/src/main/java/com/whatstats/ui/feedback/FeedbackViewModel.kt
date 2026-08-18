package com.whatstats.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.R
import com.whatstats.data.feedback.FeedbackItem
import com.whatstats.data.feedback.FeedbackRepository
import com.whatstats.data.feedback.FeedbackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedbackSendState {
    data object Idle : FeedbackSendState

    data object Sending : FeedbackSendState

    data object Sent : FeedbackSendState

    data class Error(val messageRes: Int) : FeedbackSendState

    data class CooldownActive(val remainingMinutes: Long) : FeedbackSendState
}

@HiltViewModel
class FeedbackViewModel
    @Inject
    constructor(
        private val feedbackRepository: FeedbackRepository,
    ) : ViewModel() {
        val unreadReply: StateFlow<FeedbackItem?> =
            feedbackRepository
                .observeUnreadReplies()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        val userFeedbacks: StateFlow<List<FeedbackItem>> =
            feedbackRepository
                .observeUserFeedbacks()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _sendState = MutableStateFlow<FeedbackSendState>(FeedbackSendState.Idle)
        val sendState: StateFlow<FeedbackSendState> = _sendState.asStateFlow()

        fun checkCooldown() {
            viewModelScope.launch {
                val remainingSeconds = feedbackRepository.getRemainingCooldownSeconds()
                if (remainingSeconds > 0) {
                    val remainingMinutes = ((remainingSeconds + 59) / 60).coerceAtLeast(1)
                    _sendState.value = FeedbackSendState.CooldownActive(remainingMinutes)
                } else {
                    if (_sendState.value is FeedbackSendState.CooldownActive) {
                        _sendState.value = FeedbackSendState.Idle
                    }
                }
            }
        }

        fun sendFeedback(
            type: FeedbackType,
            message: String,
        ) {
            val trimmed = message.trim()
            if (trimmed.isBlank()) {
                _sendState.value = FeedbackSendState.Error(R.string.feedback_error_empty)
                return
            }

            viewModelScope.launch {
                val remainingSeconds = feedbackRepository.getRemainingCooldownSeconds()
                if (remainingSeconds > 0) {
                    val remainingMinutes = ((remainingSeconds + 59) / 60).coerceAtLeast(1)
                    _sendState.value = FeedbackSendState.CooldownActive(remainingMinutes)
                    return@launch
                }

                _sendState.value = FeedbackSendState.Sending
                val result = feedbackRepository.sendFeedback(type, trimmed)
                if (result.isSuccess) {
                    _sendState.value = FeedbackSendState.Sent
                } else {
                    _sendState.value = FeedbackSendState.Error(R.string.feedback_error_generic)
                }
            }
        }

        fun markReplyAsRead(feedbackId: String) {
            viewModelScope.launch {
                feedbackRepository.markReplyAsRead(feedbackId)
            }
        }

        fun resetSendState() {
            _sendState.value = FeedbackSendState.Idle
        }
    }
