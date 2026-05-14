package com.vishal.interviewprepai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resumes")
data class ResumeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userPhone: String,
    val extractedText: String,
    val profession: String,
)
