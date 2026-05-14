package com.vishal.interviewprepai.viewmodel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.interviewprepai.domain.model.interview.InterviewAnswer
import com.vishal.interviewprepai.domain.model.interview.InterviewUiState
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiMockInterviewViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InterviewUiState(isLoading = true))
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText.asStateFlow()

    private val _currentQuestionText = MutableStateFlow("")
    val currentQuestionText: StateFlow<String> = _currentQuestionText.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    init {
        loadInterview(resumeText = null)
    }

    fun startInterview(resumeText: String? = null) {
        if (_state.value.isLoading) return
        loadInterview(resumeText)
    }

    fun onAnswerChange(value: String) {
        _state.value = _state.value.copy(currentAnswer = value)
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun appendSpeechText(value: String) {
        val recognizedText = value.trim()
        if (recognizedText.isBlank()) return

        _speechText.value = recognizedText

        val existingAnswer = _state.value.currentAnswer.trim()
        val mergedAnswer = buildString {
            if (existingAnswer.isNotBlank()) {
                append(existingAnswer)
            }
            if (existingAnswer.isNotBlank() && recognizedText.isNotBlank()) {
                append(' ')
            }
            append(recognizedText)
        }.trim()

        _state.value = _state.value.copy(currentAnswer = mergedAnswer)
    }

    fun clearSpeechText() {
        _speechText.value = ""
    }

    fun toggleTtsEnabled() {
        _isTtsEnabled.value = !_isTtsEnabled.value
    }

    fun setTtsEnabled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
    }

    fun submitAnswer(answer: String = _state.value.currentAnswer) {
        val current = _state.value
        val question = current.currentQuestion ?: return
        val cleanAnswer = answer.trim()
        if (cleanAnswer.isBlank()) return

        val updatedAnswers = current.submittedAnswers + InterviewAnswer(
            question = question,
            answer = cleanAnswer,
        )
        val nextIndex = current.currentQuestionIndex + 1
        val nextQuestion = current.questions.getOrNull(nextIndex)
        val nextStage = nextQuestion?.stage
        val transitionMessage = when {
            nextQuestion == null -> "${question.stage.displayName} complete. Interview finished."
            nextStage != question.stage -> "${question.stage.displayName} complete. Starting ${nextStage?.displayName}."
            else -> null
        }

        _state.value = current.copy(
            submittedAnswers = updatedAnswers,
            currentAnswer = "",
            currentQuestionIndex = if (nextQuestion == null) current.currentQuestionIndex else nextIndex,
            currentStage = nextStage,
            totalQuestionsAsked = updatedAnswers.size,
            currentQuestion = nextQuestion,
            isCompleted = nextQuestion == null,
            stageTransitionMessage = transitionMessage,
        )
        clearSpeechText()
        setListening(false)
        _currentQuestionText.value = nextQuestion?.question.orEmpty()

        current.resumeData?.let { resumeData ->
            repository.cacheStructuredMockInterviewTranscript(
                resumeData = resumeData,
                answers = updatedAnswers,
            )
        }
    }

    fun getNextQuestion() {
        submitAnswer()
    }

    private fun loadInterview(resumeText: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
            )
            clearSpeechText()
            setListening(false)
            _currentQuestionText.value = ""

            runCatching {
                repository.startStructuredMockInterview(
                    resumeText = resumeText,
                )
            }
                .onSuccess { session ->
                    val firstQuestion = session.questions.firstOrNull()
                    _state.value = InterviewUiState(
                        isLoading = false,
                        resumeData = session.resumeData,
                        config = session.config,
                        plan = session.plan,
                        questions = session.questions,
                        currentQuestionIndex = 0,
                        currentStage = firstQuestion?.stage,
                        totalQuestionsAsked = 0,
                        currentQuestion = firstQuestion,
                        submittedAnswers = emptyList(),
                        isCompleted = firstQuestion == null,
                        stageTransitionMessage = firstQuestion?.stage?.displayName?.let { "Starting $it." },
                    )
                    _currentQuestionText.value = firstQuestion?.question.orEmpty()
                }
                .onFailure { throwable ->
                    _state.value = InterviewUiState(
                        isLoading = false,
                        error = throwable.message ?: "Failed to start interview",
                    )
                    _currentQuestionText.value = ""
                }
        }
    }
}
