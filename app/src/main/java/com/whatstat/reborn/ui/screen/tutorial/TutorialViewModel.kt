package com.whatstat.reborn.ui.screen.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.cache.CacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(TutorialState())
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    fun setTutorialCompleted() {
        viewModelScope.launch {
            cacheManager.setHasSeenTutorial()
            _state.value = _state.value.copy(isCompleted = true)
        }
    }
    
    fun skipTutorial() {
        setTutorialCompleted()
    }
}

data class TutorialState(
    val isCompleted: Boolean = false,
    val currentPage: Int = 0
)