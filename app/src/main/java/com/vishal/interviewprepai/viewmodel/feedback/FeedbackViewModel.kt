package com.vishal.interviewprepai.viewmodel.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.vishal.interviewprepai.domain.model.FeedbackSummary
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedbackUiState(
    val isLoading: Boolean = true,
    val summary: FeedbackSummary? = null,
)

class FeedbackViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {
    private val tag = "FeedbackVM"
    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d(tag, "init: getFeedback()")
            _state.value = FeedbackUiState(isLoading = true)
            runCatching { repository.getFeedback() }
                .onSuccess { summary ->
                    Log.d(tag, "getFeedback: success")
                    _state.value = FeedbackUiState(isLoading = false, summary = summary)
                }
                .onFailure { t ->
                    Log.e(tag, "getFeedback failed", t)
                    _state.value = FeedbackUiState(isLoading = false, summary = null)
                }
        }
    }
}

