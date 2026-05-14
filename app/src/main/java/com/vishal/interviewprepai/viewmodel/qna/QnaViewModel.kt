package com.vishal.interviewprepai.viewmodel.qna

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.interviewprepai.domain.model.DifficultyLevel
import com.vishal.interviewprepai.domain.model.QuestionAnswer
import com.vishal.interviewprepai.domain.model.RoundType
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import com.vishal.interviewprepai.ui.screens.qna.EmptyStateItem
import com.vishal.interviewprepai.ui.screens.qna.HrHeaderItem
import com.vishal.interviewprepai.ui.screens.qna.HrQuestionItem
import com.vishal.interviewprepai.ui.screens.qna.LoadingItem
import com.vishal.interviewprepai.ui.screens.qna.QnaUiItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalEasyHeaderItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalEasyItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalHardHeaderItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalHardItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalMediumHeaderItem
import com.vishal.interviewprepai.ui.screens.qna.TechnicalMediumItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QnaUiState(
    val isLoading: Boolean = true,
    val questions: List<QuestionAnswer> = emptyList(),
    val items: List<QnaUiItem> = listOf(LoadingItem()),
    val expandedId: String? = null,
    val errorMessage: String? = null,
)

class QnaViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {
    private val tag = "QnaVM"
    private val _state = MutableStateFlow(QnaUiState())
    val state: StateFlow<QnaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d(tag, "init: load questions")
            runCatching { repository.getSuggestedQuestions() }
                .onSuccess { items ->
                    Log.d(tag, "init: loaded items=${items.size}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        questions = items,
                        items = mapToUiItems(items),
                        errorMessage = null,
                    )
                }
                .onFailure { t ->
                    Log.e(tag, "init failed", t)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        items = listOf(EmptyStateItem()),
                        errorMessage = t.message ?: "Failed to load questions",
                    )
                }
        }
    }

    fun toggle(id: String) {
        _state.value = _state.value.copy(
            expandedId = if (_state.value.expandedId == id) null else id,
        )
    }

    fun generateMore() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            Log.d(tag, "generateMore: click")
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.generateMoreAdvancedQuestions() }
                .onSuccess { items ->
                    Log.d(tag, "generateMore: success items=${items.size}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        questions = items,
                        items = mapToUiItems(items),
                    )
                }
                .onFailure { t ->
                    Log.e(tag, "generateMore failed", t)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Failed to generate more questions",
                    )
                }
        }
    }

    private fun mapToUiItems(
        questions: List<QuestionAnswer>,
    ): List<QnaUiItem> {
        if (questions.isEmpty()) return listOf(EmptyStateItem())

        val grouped = questions.groupBy(::resolveRoundType)
        return buildList {
            RoundType.entries.forEach { roundType ->
                val questionsForRound = grouped[roundType].orEmpty()
                if (questionsForRound.isEmpty()) return@forEach

                add(sectionHeaderFor(roundType))

                questionsForRound.forEach { question ->
                    add(questionItemFor(question, roundType))
                }
            }
        }
    }

    private fun sectionHeaderFor(
        roundType: RoundType,
    ): QnaUiItem {
        return when (roundType) {
            RoundType.HR -> HrHeaderItem(
                title = roundType.title,
                subtitle = roundType.subtitle,
            )
            RoundType.TECHNICAL_1 -> TechnicalEasyHeaderItem(
                title = roundType.title,
                subtitle = roundType.subtitle,
            )
            RoundType.TECHNICAL_2 -> TechnicalMediumHeaderItem(
                title = roundType.title,
                subtitle = roundType.subtitle,
            )
            RoundType.TECHNICAL_3 -> TechnicalHardHeaderItem(
                title = roundType.title,
                subtitle = roundType.subtitle,
            )
        }
    }

    private fun questionItemFor(
        question: QuestionAnswer,
        roundType: RoundType,
    ): QnaUiItem {
        val difficulty = resolveDifficulty(question, roundType)
        return when (roundType) {
            RoundType.HR -> HrQuestionItem(
                id = question.id,
                question = question.question,
                answer = question.suggestedAnswer,
                difficulty = difficulty,
                domain = question.domain,
            )
            RoundType.TECHNICAL_1 -> TechnicalEasyItem(
                id = question.id,
                question = question.question,
                answer = question.suggestedAnswer,
                difficulty = difficulty,
                domain = question.domain,
            )
            RoundType.TECHNICAL_2 -> TechnicalMediumItem(
                id = question.id,
                question = question.question,
                answer = question.suggestedAnswer,
                difficulty = difficulty,
                domain = question.domain,
            )
            RoundType.TECHNICAL_3 -> TechnicalHardItem(
                id = question.id,
                question = question.question,
                answer = question.suggestedAnswer,
                difficulty = difficulty,
                domain = question.domain,
            )
        }
    }

    private fun resolveRoundType(
        item: QuestionAnswer,
    ): RoundType {
        item.roundType?.let { return it }

        val normalized = item.question.lowercase()
        return when {
            normalized.contains("team") ||
                normalized.contains("strength") ||
                normalized.contains("weakness") ||
                normalized.contains("hire") ||
                normalized.contains("career") ||
                normalized.contains("conflict") ||
                normalized.contains("leader") ||
                normalized.contains("salary") ||
                normalized.contains("pressure") ||
                normalized.contains("communicat") ||
                normalized.contains("behavior") ||
                normalized.contains("situat") ||
                normalized.contains("motivat") ||
                normalized.contains("yourself") -> RoundType.HR

            normalized.contains("architecture") ||
                normalized.contains("system design") ||
                normalized.contains("scal") ||
                normalized.contains("optimization") ||
                normalized.contains("production") ||
                normalized.contains("incident") ||
                normalized.contains("trade-off") ||
                normalized.contains("debug") -> RoundType.TECHNICAL_3

            normalized.contains("implement") ||
                normalized.contains("practical") ||
                normalized.contains("api") ||
                normalized.contains("test") ||
                normalized.contains("design") ||
                normalized.contains("usage") -> RoundType.TECHNICAL_2

            else -> RoundType.TECHNICAL_1
        }
    }

    private fun resolveDifficulty(
        item: QuestionAnswer,
        roundType: RoundType,
    ): DifficultyLevel {
        item.difficultyLevel?.let { return it }

        return when (roundType) {
            RoundType.HR,
            RoundType.TECHNICAL_1,
            -> DifficultyLevel.EASY
            RoundType.TECHNICAL_2 -> DifficultyLevel.MEDIUM
            RoundType.TECHNICAL_3 -> DifficultyLevel.HARD
        }
    }
}
