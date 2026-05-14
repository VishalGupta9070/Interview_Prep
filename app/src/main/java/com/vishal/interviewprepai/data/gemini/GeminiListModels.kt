package com.vishal.interviewprepai.data.gemini

import com.google.gson.annotations.SerializedName

data class GeminiListModelsResponse(
    @SerializedName("models")
    val models: List<GeminiModel> = emptyList(),
)

data class GeminiModel(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("supportedGenerationMethods")
    val supportedGenerationMethods: List<String> = emptyList(),
)
