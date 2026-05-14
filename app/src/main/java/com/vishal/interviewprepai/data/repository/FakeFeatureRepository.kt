package com.vishal.interviewprepai.data.repository

import com.vishal.interviewprepai.domain.model.Feature
import com.vishal.interviewprepai.domain.repository.FeatureRepository

class FakeFeatureRepository : FeatureRepository {
    override suspend fun getHomeFeatures(): List<Feature> {
        return listOf(
            Feature(
                id = "resume",
                title = "Resume → Questions",
                subtitle = "Upload your resume and get tailored interview questions.",
                badge = "Recommended",
            ),
            Feature(
                id = "qna",
                title = "Questions & Answers",
                subtitle = "Review curated questions with suggested answers.",
                badge = "Practice",
            ),
            Feature(
                id = "mock",
                title = "Mock Interview",
                subtitle = "Chat-based mock interview with an AI coach.",
                badge = "Live",
            ),
        )
    }
}

