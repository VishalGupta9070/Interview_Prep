package com.vishal.interviewprepai.domain.repository

import com.vishal.interviewprepai.domain.model.Feature

interface FeatureRepository {
    suspend fun getHomeFeatures(): List<Feature>
}

