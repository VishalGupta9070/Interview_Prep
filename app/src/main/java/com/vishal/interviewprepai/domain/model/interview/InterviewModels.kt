package com.vishal.interviewprepai.domain.model.interview

enum class InterviewStage {
    HR,
    TECH1,
    TECH2,
    TECH3,
    ;

    val displayName: String
        get() = when (this) {
            HR -> "Round 1: HR"
            TECH1 -> "Round 2: Technical (Basics)"
            TECH2 -> "Round 3: Technical (Intermediate)"
            TECH3 -> "Round 4: Technical (Advanced)"
        }
}

enum class QuestionDifficulty(
    val displayName: String,
) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
}

enum class ExperienceBand {
    EARLY_CAREER,
    MID_LEVEL,
    SENIOR,
}

data class InterviewConfig(
    val totalQuestions: Int,
    val includeResumeQuestions: Boolean,
    val stageQuestionDistribution: Map<InterviewStage, Int>,
) {
    fun stageCount(stage: InterviewStage): Int = stageQuestionDistribution[stage] ?: 0

    val effectiveTotalQuestions: Int
        get() = stageQuestionDistribution.values.sum()
}

data class ResumeData(
    val primaryDomain: String,
    val profession: String,
    val primarySkills: List<String>,
    val secondarySkills: List<String>,
    val yearsOfExperience: Int,
    val resumeText: String,
) {
    val allSkills: List<String>
        get() = (primarySkills + secondarySkills).distinct()

    val experienceBand: ExperienceBand
        get() = when {
            yearsOfExperience <= 1 -> ExperienceBand.EARLY_CAREER
            yearsOfExperience <= 4 -> ExperienceBand.MID_LEVEL
            else -> ExperienceBand.SENIOR
        }
}

data class InterviewQuestion(
    val id: String,
    val stage: InterviewStage,
    val difficulty: QuestionDifficulty,
    val domain: String,
    val question: String,
    val focusArea: String? = null,
) {
    val text: String
        get() = question
}

data class InterviewStagePlan(
    val stage: InterviewStage,
    val questionCount: Int,
    val difficulty: QuestionDifficulty,
    val promptDifficultyLabel: String,
    val domain: String,
    val includeResumeDeepDive: Boolean = false,
)

data class InterviewPlan(
    val stagePlans: List<InterviewStagePlan>,
) {
    private val stagePlanMap = stagePlans.associateBy { it.stage }

    fun countFor(stage: InterviewStage): Int = stagePlanMap[stage]?.questionCount ?: 0

    fun difficultyFor(stage: InterviewStage): QuestionDifficulty? = stagePlanMap[stage]?.difficulty

    fun stagePlan(stage: InterviewStage): InterviewStagePlan? = stagePlanMap[stage]

    val totalQuestions: Int
        get() = stagePlans.sumOf { it.questionCount }
}

data class InterviewAnswer(
    val question: InterviewQuestion,
    val answer: String,
)

data class MockInterviewSession(
    val resumeData: ResumeData,
    val config: InterviewConfig,
    val plan: InterviewPlan,
    val questions: List<InterviewQuestion>,
)

data class InterviewUiState(
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
    val resumeData: ResumeData? = null,
    val config: InterviewConfig? = null,
    val plan: InterviewPlan? = null,
    val questions: List<InterviewQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val currentStage: InterviewStage? = null,
    val totalQuestionsAsked: Int = 0,
    val currentQuestion: InterviewQuestion? = null,
    val currentAnswer: String = "",
    val submittedAnswers: List<InterviewAnswer> = emptyList(),
    val stageTransitionMessage: String? = null,
) {
    val totalQuestions: Int
        get() = config?.effectiveTotalQuestions ?: plan?.totalQuestions ?: questions.size

    private val activeQuestionNumber: Int
        get() = when {
            totalQuestions == 0 -> 0
            isCompleted -> totalQuestions
            currentQuestion != null -> (currentQuestionIndex + 1).coerceAtMost(totalQuestions)
            else -> totalQuestionsAsked.coerceAtMost(totalQuestions)
        }

    val progressLabel: String
        get() = "Question $activeQuestionNumber / $totalQuestions"

    val currentStageLabel: String
        get() = currentStage?.displayName.orEmpty()

    val currentDifficultyLabel: String
        get() = currentQuestion?.difficulty?.displayName.orEmpty()
}
