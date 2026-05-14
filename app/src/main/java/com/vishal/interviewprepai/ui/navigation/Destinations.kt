package com.vishal.interviewprepai.ui.navigation

sealed class Destinations(val route: String) {
    data object Splash : Destinations("splash")
    data object Login : Destinations("login")
    data object Home : Destinations("home")
    data object ResumeUpload : Destinations("resume_upload")
    data object Qna : Destinations("qna")
    data object MockInterview : Destinations("mock_interview")
    data object Feedback : Destinations("feedback")
}
