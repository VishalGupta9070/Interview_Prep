package com.vishal.interviewprepai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vishal.interviewprepai.data.local.entity.QuestionEntity

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE userPhone = :phone ORDER BY id ASC")
    suspend fun getByUserPhone(phone: String): List<QuestionEntity>

    @Query("DELETE FROM questions WHERE userPhone = :phone")
    suspend fun deleteByUserPhone(phone: String)
}
