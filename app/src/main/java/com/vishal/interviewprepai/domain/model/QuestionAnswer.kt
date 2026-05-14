package com.vishal.interviewprepai.domain.model

enum class RoundType(
    val title: String,
    val subtitle: String,
) {
    HR(
        title = "HR Round",
        subtitle = "Introduction, communication, teamwork, conflict handling, and career goals",
    ),
    TECHNICAL_1(
        title = "Technical Round 1",
        subtitle = "Easy foundations, definitions, and beginner concepts",
    ),
    TECHNICAL_2(
        title = "Technical Round 2",
        subtitle = "Medium practical usage, design understanding, and architecture basics",
    ),
    TECHNICAL_3(
        title = "Technical Round 3",
        subtitle = "Hard scenarios, optimization, debugging, and system design",
    ),
}

enum class DifficultyLevel {
    EASY,
    MEDIUM,
    HARD,
}

data class QuestionAnswer(
    val id: String,
    val question: String,
    val suggestedAnswer: String,
    val roundType: RoundType? = null,
    val difficultyLevel: DifficultyLevel? = null,
    val domain: String? = null,
)
