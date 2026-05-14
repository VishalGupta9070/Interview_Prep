package com.vishal.interviewprepai.data.interview

import com.vishal.interviewprepai.data.local.dao.ResumeDao
import com.vishal.interviewprepai.data.session.SessionManager
import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.ResumeData
import com.vishal.interviewprepai.domain.repository.AiInterviewRepository

class AiInterviewRepositoryImpl(
    private val sessionManager: SessionManager,
    private val resumeDao: ResumeDao,
    private val analyzer: ResumeAnalyzer,
    private val engine: QuestionFlowEngine,
) : AiInterviewRepository {
    override suspend fun loadResumeText(): String? {
        val phone = sessionManager.userPhone()?.trim().orEmpty()
        if (phone.isBlank()) return null
        return resumeDao.getLatestByUserPhone(phone)?.extractedText
    }

    override suspend fun analyzeResume(resumeText: String): ResumeData = analyzer.analyze(resumeText)

    override suspend fun generateQuestions(resumeData: ResumeData): List<InterviewQuestion> {
        return engine.buildQuestionSet(resumeData)
    }
}

