package com.vishal.interviewprepai.data.di

import android.content.Context
import com.vishal.interviewprepai.BuildConfig
import com.vishal.interviewprepai.data.gemini.GeminiInterviewRepository
import com.vishal.interviewprepai.data.gemini.GeminiServiceFactory
import com.vishal.interviewprepai.data.local.dao.UserDao
import com.vishal.interviewprepai.data.local.db.AppDatabase
import com.vishal.interviewprepai.data.session.SessionManager
import com.vishal.interviewprepai.domain.repository.InterviewRepository

object AppContainer {
    @Volatile
    private var interviewRepository: InterviewRepository? = null
    @Volatile
    private var sessionManager: SessionManager? = null
    @Volatile
    private var database: AppDatabase? = null

    fun sessionManager(context: Context): SessionManager {
        val existing = sessionManager
        if (existing != null) return existing
        return synchronized(this) {
            val again = sessionManager
            if (again != null) again
            else SessionManager(context.applicationContext).also { sessionManager = it }
        }
    }

    fun appDatabase(context: Context): AppDatabase {
        val existing = database
        if (existing != null) return existing
        return synchronized(this) {
            val again = database
            if (again != null) again
            else AppDatabase.getInstance(context.applicationContext).also { database = it }
        }
    }

    fun userDao(context: Context): UserDao = appDatabase(context).userDao()

    fun interviewRepository(context: Context): InterviewRepository {
        val existing = interviewRepository
        if (existing != null) return existing

        return synchronized(this) {
            val again = interviewRepository
            if (again != null) again
            else {
                val appContext = context.applicationContext
                val service = GeminiServiceFactory.create()
                GeminiInterviewRepository(
                    context = appContext,
                    api = service,
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    sessionManager = sessionManager(appContext),
                    resumeDao = appDatabase(appContext).resumeDao(),
                    questionDao = appDatabase(appContext).questionDao(),
                ).also { interviewRepository = it }
            }
        }
    }
}

