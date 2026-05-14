package com.vishal.interviewprepai.domain.model

data class FeedbackSummary(
    val score: Int,
    val highlights: List<String>,
    val improvements: List<String>,
)

