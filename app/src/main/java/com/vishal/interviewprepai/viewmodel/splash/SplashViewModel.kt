package com.vishal.interviewprepai.viewmodel.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.interviewprepai.data.session.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SplashUiState(
    val isReadyToNavigate: Boolean = false,
    val navigateToHome: Boolean = false,
)

class SplashViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(1400)
            _state.value = SplashUiState(
                isReadyToNavigate = true,
                navigateToHome = sessionManager.isLoggedIn(),
            )
        }
    }
}

