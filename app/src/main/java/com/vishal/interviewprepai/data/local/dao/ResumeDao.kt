package com.vishal.interviewprepai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vishal.interviewprepai.data.local.entity.ResumeEntity

@Dao
interface ResumeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(resume: ResumeEntity)

    @Query("SELECT * FROM resumes WHERE userPhone = :phone ORDER BY id DESC")
    suspend fun getByUserPhone(phone: String): List<ResumeEntity>

    @Query("SELECT * FROM resumes WHERE userPhone = :phone ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByUserPhone(phone: String): ResumeEntity?

    @Query("DELETE FROM resumes WHERE userPhone = :phone")
    suspend fun deleteByUserPhone(phone: String)
}
