package com.vishal.interviewprepai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val phoneNumber: String,
    val password: String,
)
