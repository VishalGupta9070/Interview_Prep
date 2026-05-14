package com.vishal.interviewprepai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vishal.interviewprepai.ui.InterviewPrepApp
import com.vishal.interviewprepai.ui.theme.InterviewPrepAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InterviewPrepAITheme {
                InterviewPrepApp()
            }
        }
    }
}

