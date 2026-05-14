package com.vishal.interviewprepai.domain.repository

import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.ResumeData

interface AiInterviewRepository {
    suspend fun loadResumeText(): String?
    suspend fun analyzeResume(resumeText: String): ResumeData
    suspend fun generateQuestions(resumeData: ResumeData): List<InterviewQuestion>
}

