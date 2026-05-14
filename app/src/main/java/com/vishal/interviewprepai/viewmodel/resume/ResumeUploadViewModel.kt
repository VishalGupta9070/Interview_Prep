package com.vishal.interviewprepai.viewmodel.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.util.Log
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResumeUiState(
    val selectedFileName: String? = null,
    val selectedUri: Uri? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
)

class ResumeUploadViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {
    private val tag = "ResumeUploadVM"
    private val _state = MutableStateFlow(ResumeUiState())
    val state: StateFlow<ResumeUiState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri, fileName: String?) {
        Log.d(tag, "onFilePicked: name=${fileName ?: "resume.pdf"} uri=$uri")
        _state.value = _state.value.copy(
            selectedFileName = fileName ?: "resume.pdf",
            selectedUri = uri,
            error = null,
        )
    }

    fun generateQuestions(onSuccess: () -> Unit) {
        val uri = _state.value.selectedUri ?: return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            Log.d(tag, "generateQuestions: start")
            _state.value = _state.value.copy(isLoading = true, isSuccess = false, error = null)
            runCatching {
                repository.generateQuestionsFromResume(uri)
            }.onSuccess {
                Log.d(tag, "generateQuestions: success")
                _state.value = _state.value.copy(isLoading = false, isSuccess = true, error = null)
                onSuccess()
            }.onFailure { t ->
                Log.e(tag, "generateQuestions failed", t)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isSuccess = false,
                    error = t.message ?: "Something went wrong",
                )
            }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}

