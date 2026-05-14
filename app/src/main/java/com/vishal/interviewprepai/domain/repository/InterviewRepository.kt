package com.vishal.interviewprepai.domain.repository

import android.net.Uri
import com.vishal.interviewprepai.domain.model.ChatMessage
import com.vishal.interviewprepai.domain.model.FeedbackSummary
import com.vishal.interviewprepai.domain.model.QuestionAnswer
import com.vishal.interviewprepai.domain.model.interview.InterviewAnswer
import com.vishal.interviewprepai.domain.model.interview.MockInterviewSession
import com.vishal.interviewprepai.domain.model.interview.ResumeData

interface InterviewRepository {
    suspend fun getSuggestedQuestions(): List<QuestionAnswer>
    suspend fun generateQuestionsFromResume(pdfUri: Uri): List<QuestionAnswer>
    suspend fun generateMoreAdvancedQuestions(): List<QuestionAnswer>
    suspend fun logoutCurrentUser()

    suspend fun startStructuredMockInterview(
        resumeText: String? = null,
    ): MockInterviewSession
    fun cacheStructuredMockInterviewTranscript(
        resumeData: ResumeData,
        answers: List<InterviewAnswer>,
    )

    suspend fun startMockInterview(): List<ChatMessage>
    suspend fun sendMockInterviewMessage(conversation: List<ChatMessage>): ChatMessage

    suspend fun getFeedback(): FeedbackSummary
}
