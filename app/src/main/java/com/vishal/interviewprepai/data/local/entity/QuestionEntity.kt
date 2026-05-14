package com.vishal.interviewprepai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userPhone: String,
    val question: String,
    val answer: String,
    val roundType: String? = null,
    val difficultyLevel: String? = null,
    val domain: String? = null,
)
