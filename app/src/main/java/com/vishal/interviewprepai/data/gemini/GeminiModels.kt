package com.vishal.interviewprepai.data.gemini

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("parts")
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    @SerializedName("text")
    val text: String,
)

data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidate> = emptyList(),
)

data class GeminiCandidate(
    @SerializedName("content")
    val content: GeminiCandidateContent? = null,
)

data class GeminiCandidateContent(
    @SerializedName("parts")
    val parts: List<GeminiPart> = emptyList(),
)

data class GeminiGenerationConfig(
    @SerializedName("temperature")
    val temperature: Double? = null,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int? = null,
)

