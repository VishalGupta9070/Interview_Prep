package com.vishal.interviewprepai.viewmodel.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.interviewprepai.data.local.dao.UserDao
import com.vishal.interviewprepai.data.local.entity.UserEntity
import com.vishal.interviewprepai.data.session.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val sessionManager: SessionManager,
    private val userDao: UserDao,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) {
        val digits = value.filter(Char::isDigit).take(10)
        _state.value = _state.value.copy(phone = digits, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val current = _state.value
        if (current.isLoading) return
        if (current.phone.length != 10) {
            _state.value = current.copy(error = "Enter a valid 10-digit phone number")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            // Keep a tiny delay for button feedback consistency.
            delay(250)
            userDao.upsert(
                UserEntity(
                    phoneNumber = current.phone,
                    password = current.password,
                ),
            )
            sessionManager.login(current.phone)
            _state.value = _state.value.copy(isLoading = false, isLoggedIn = true, error = null)
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}
