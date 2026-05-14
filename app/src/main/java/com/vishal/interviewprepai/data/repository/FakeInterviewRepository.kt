package com.vishal.interviewprepai.data.repository

import android.net.Uri
import com.vishal.interviewprepai.data.interview.QuestionFlowEngine
import com.vishal.interviewprepai.domain.model.ChatMessage
import com.vishal.interviewprepai.domain.model.DifficultyLevel
import com.vishal.interviewprepai.domain.model.FeedbackSummary
import com.vishal.interviewprepai.domain.model.QuestionAnswer
import com.vishal.interviewprepai.domain.model.RoundType
import com.vishal.interviewprepai.domain.model.interview.InterviewAnswer
import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.InterviewStage
import com.vishal.interviewprepai.domain.model.interview.MockInterviewSession
import com.vishal.interviewprepai.domain.model.interview.ResumeData
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import com.vishal.interviewprepai.ui.components.ChatSender

class FakeInterviewRepository : InterviewRepository {
    private val questionFlowEngine = QuestionFlowEngine()

    override suspend fun getSuggestedQuestions(): List<QuestionAnswer> = samplePreparationQuestions()

    override suspend fun generateQuestionsFromResume(pdfUri: Uri): List<QuestionAnswer> = samplePreparationQuestions()

    override suspend fun generateMoreAdvancedQuestions(): List<QuestionAnswer> {
        return samplePreparationQuestions() + listOf(
            QuestionAnswer(
                id = "q5",
                question = "How would you explain the benefits of structured concurrency in Kotlin coroutines?",
                suggestedAnswer = "Describe parent-child job relationships, cancellation propagation, and how structured scopes make lifecycle management, error handling, and readability much safer.",
                roundType = RoundType.TECHNICAL_2,
                difficultyLevel = DifficultyLevel.MEDIUM,
                domain = "Android",
            ),
            QuestionAnswer(
                id = "q6",
                question = "How would you redesign a data layer that is causing slow startup and flaky offline behavior?",
                suggestedAnswer = "Talk through instrumentation first, identify network and disk bottlenecks, introduce caching and clearer source-of-truth ownership, and validate the redesign with metrics and failure-path testing.",
                roundType = RoundType.TECHNICAL_3,
                difficultyLevel = DifficultyLevel.HARD,
                domain = "Android",
            ),
        )
    }

    override suspend fun logoutCurrentUser() {
        // no-op for fake repository
    }

    override suspend fun startStructuredMockInterview(
        resumeText: String?,
    ): MockInterviewSession {
        val resumeData = ResumeData(
            primaryDomain = "Android",
            profession = "Android Developer",
            primarySkills = listOf("Kotlin", "Jetpack Compose", "MVVM"),
            secondarySkills = listOf("Room", "Coroutines"),
            yearsOfExperience = 3,
            resumeText = resumeText ?: "Android developer with Kotlin and Compose experience.",
        )
        val config = questionFlowEngine.buildConfig(
            resume = resumeData,
        )
        val plan = questionFlowEngine.buildPlan(
            resume = resumeData,
            config = config,
        )
        val questions = buildList {
            fun addStageQuestions(stage: InterviewStage, prefix: String, focusArea: String) {
                val stagePlan = plan.stagePlan(stage) ?: return
                val count = stagePlan.questionCount
                if (count <= 0) return
                repeat(count) { index ->
                    add(
                        InterviewQuestion(
                            id = "${stage.name.lowercase()}_${index + 1}",
                            stage = stage,
                            difficulty = stagePlan.difficulty,
                            domain = stagePlan.domain,
                            question = "$prefix ${index + 1}.",
                            focusArea = focusArea,
                        ),
                    )
                }
            }

            addStageQuestions(
                stage = InterviewStage.HR,
                prefix = "Tell me about a communication or teamwork situation",
                focusArea = "HR",
            )
            addStageQuestions(
                stage = InterviewStage.TECH1,
                prefix = "Explain a foundation-level Android or Kotlin concept",
                focusArea = "Kotlin",
            )
            addStageQuestions(
                stage = InterviewStage.TECH2,
                prefix = "Describe how you would implement or debug a production-ready Compose feature",
                focusArea = "Jetpack Compose",
            )
            addStageQuestions(
                stage = InterviewStage.TECH3,
                prefix = "Walk through an advanced architecture, system design, or resume-based project decision",
                focusArea = "Resume",
            )
        }
        return MockInterviewSession(
            resumeData = resumeData,
            config = config,
            plan = plan,
            questions = questions,
        )
    }

    override fun cacheStructuredMockInterviewTranscript(
        resumeData: ResumeData,
        answers: List<InterviewAnswer>,
    ) {
        // no-op for fake repository
    }

    override suspend fun startMockInterview(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "m1",
                sender = ChatSender.AI,
                text = "Hi! I’ll be your interviewer today. Ready to start?",
            ),
            ChatMessage(
                id = "m2",
                sender = ChatSender.USER,
                text = "Yes, let’s go.",
            ),
            ChatMessage(
                id = "m3",
                sender = ChatSender.AI,
                text = "Great. First question: tell me about yourself.",
            ),
        )
    }

    override suspend fun sendMockInterviewMessage(conversation: List<ChatMessage>): ChatMessage {
        return ChatMessage(
            id = "m_next",
            sender = ChatSender.AI,
            text = "Thanks — can you share a specific example?",
        )
    }

    override suspend fun getFeedback(): FeedbackSummary {
        return FeedbackSummary(
            score = 82,
            highlights = listOf(
                "Clear structure and confident tone.",
                "Good use of concrete examples.",
                "Strong alignment with role requirements.",
            ),
            improvements = listOf(
                "Tighten answers to under 90 seconds.",
                "Add one measurable impact metric.",
                "Ask a clarifying question before deep dives.",
            ),
        )
    }

    private fun samplePreparationQuestions(): List<QuestionAnswer> {
        return listOf(
            QuestionAnswer(
                id = "q1",
                question = "Tell me about yourself.",
                suggestedAnswer = "Give a crisp summary of your recent role, core skills, and the kind of impact you want to create next.",
                roundType = RoundType.HR,
                difficultyLevel = DifficultyLevel.EASY,
                domain = "HR",
            ),
            QuestionAnswer(
                id = "q2",
                question = "What is the difference between val and var in Kotlin?",
                suggestedAnswer = "Explain immutability versus mutability, when each is appropriate, and why preferring val improves safety and readability.",
                roundType = RoundType.TECHNICAL_1,
                difficultyLevel = DifficultyLevel.EASY,
                domain = "Android",
            ),
            QuestionAnswer(
                id = "q3",
                question = "How would you structure UI state for a Compose screen backed by a ViewModel?",
                suggestedAnswer = "Describe a single source of truth in the ViewModel, expose immutable state, collect it in Compose, and model loading, success, and error states explicitly.",
                roundType = RoundType.TECHNICAL_2,
                difficultyLevel = DifficultyLevel.MEDIUM,
                domain = "Android",
            ),
            QuestionAnswer(
                id = "q4",
                question = "How would you debug and redesign a feature that is causing jank in a complex Compose screen?",
                suggestedAnswer = "Start with measurement, identify recomposition and rendering hotspots, reduce unnecessary state reads, split responsibilities, and verify improvements with profiling data.",
                roundType = RoundType.TECHNICAL_3,
                difficultyLevel = DifficultyLevel.HARD,
                domain = "Android",
            ),
        )
    }
}
