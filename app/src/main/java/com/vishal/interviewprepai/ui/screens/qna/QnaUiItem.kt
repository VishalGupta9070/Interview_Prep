package com.vishal.interviewprepai.ui.screens.qna

import com.vishal.interviewprepai.domain.model.DifficultyLevel
import com.vishal.interviewprepai.domain.model.RoundType

sealed interface QnaUiItem {
    val stableKey: String
}

data class HrHeaderItem(
    val title: String,
    val subtitle: String? = null,
) : QnaUiItem {
    override val stableKey: String = "header_${RoundType.HR.name}"
}

data class HrQuestionItem(
    val id: String,
    val question: String,
    val answer: String,
    val difficulty: DifficultyLevel,
    val domain: String? = null,
) : QnaUiItem {
    override val stableKey: String = "hr_$id"
}

data class TechnicalEasyHeaderItem(
    val title: String,
    val subtitle: String? = null,
) : QnaUiItem {
    override val stableKey: String = "header_${RoundType.TECHNICAL_1.name}"
}

data class TechnicalEasyItem(
    val id: String,
    val question: String,
    val answer: String,
    val difficulty: DifficultyLevel,
    val domain: String? = null,
) : QnaUiItem {
    override val stableKey: String = "easy_$id"
}

data class TechnicalMediumHeaderItem(
    val title: String,
    val subtitle: String? = null,
) : QnaUiItem {
    override val stableKey: String = "header_${RoundType.TECHNICAL_2.name}"
}

data class TechnicalMediumItem(
    val id: String,
    val question: String,
    val answer: String,
    val difficulty: DifficultyLevel,
    val domain: String? = null,
) : QnaUiItem {
    override val stableKey: String = "medium_$id"
}

data class TechnicalHardHeaderItem(
    val title: String,
    val subtitle: String? = null,
) : QnaUiItem {
    override val stableKey: String = "header_${RoundType.TECHNICAL_3.name}"
}

data class TechnicalHardItem(
    val id: String,
    val question: String,
    val answer: String,
    val difficulty: DifficultyLevel,
    val domain: String? = null,
) : QnaUiItem {
    override val stableKey: String = "hard_$id"
}

data class LoadingItem(
    val message: String = "Preparing your interview sections...",
) : QnaUiItem {
    override val stableKey: String = "loading"
}

data class EmptyStateItem(
    val message: String = "Upload a resume to generate grouped interview preparation questions.",
) : QnaUiItem {
    override val stableKey: String = "empty"
}
