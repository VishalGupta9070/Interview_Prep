package com.vishal.interviewprepai.data.gemini

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/{model}:generateContent")
    suspend fun generateContent(
        @Path(value = "model", encoded = true) model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest,
    ): GeminiResponse

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String,
    ): GeminiListModelsResponse
}
