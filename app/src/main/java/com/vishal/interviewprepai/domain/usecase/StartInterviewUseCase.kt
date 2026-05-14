package com.vishal.interviewprepai.domain.usecase

import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.ResumeData
import com.vishal.interviewprepai.domain.repository.AiInterviewRepository

data class InterviewStartResult(
    val resumeData: ResumeData,
    val questions: List<InterviewQuestion>,
)

class StartInterviewUseCase(
    private val repository: AiInterviewRepository,
) {
    suspend operator fun invoke(resumeText: String?): InterviewStartResult {
        val sourceText = resumeText?.trim().takeUnless { it.isNullOrBlank() }
            ?: repository.loadResumeText().orEmpty()

        val safeResumeText = if (sourceText.isBlank()) {
            "Software developer with Kotlin, Android, APIs, and problem-solving experience."
        } else sourceText

        val resumeData = repository.analyzeResume(safeResumeText)
        val questions = repository.generateQuestions(resumeData)
        return InterviewStartResult(resumeData = resumeData, questions = questions)
    }
}

