package com.vishal.interviewprepai.viewmodel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.vishal.interviewprepai.domain.model.ChatMessage
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import com.vishal.interviewprepai.ui.components.ChatSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MockInterviewUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
)

class MockInterviewViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {

    private val tag = "MockInterviewVM"
    private val _state = MutableStateFlow(MockInterviewUiState())
    val state: StateFlow<MockInterviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d(tag, "init: startMockInterview()")
            runCatching { repository.startMockInterview() }
                .onSuccess { seed ->
                    Log.d(tag, "init: seedSize=${seed.size}")
                    _state.value = _state.value.copy(isLoading = false, messages = seed)
                }
                .onFailure { t ->
                    Log.e(tag, "init failed", t)
                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        Log.d(tag, "send: userMessage chars=${text.length}")

        val user = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = ChatSender.USER,
            text = text,
        )
        _state.value = _state.value.copy(
            input = "",
            messages = _state.value.messages + user,
        )

        viewModelScope.launch {
            val conversation = _state.value.messages
            runCatching { repository.sendMockInterviewMessage(conversation) }
                .onSuccess { ai ->
                    Log.d(tag, "send: aiResponse chars=${ai.text.length}")
                    _state.value = _state.value.copy(messages = _state.value.messages + ai)
                }
                .onFailure { t ->
                    Log.e(tag, "send failed", t)
                    // No UI changes allowed, so we keep the conversation and rely on logs for debugging.
                }
        }
    }
}

