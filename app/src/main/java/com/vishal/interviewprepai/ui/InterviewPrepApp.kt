package com.vishal.interviewprepai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vishal.interviewprepai.data.di.AppContainer
import com.vishal.interviewprepai.ui.navigation.Destinations
import com.vishal.interviewprepai.ui.screens.chat.AiMockInterviewRoute
import com.vishal.interviewprepai.ui.screens.feedback.FeedbackRoute
import com.vishal.interviewprepai.ui.screens.home.HomeRoute
import com.vishal.interviewprepai.ui.screens.login.LoginRoute
import com.vishal.interviewprepai.ui.screens.qna.QuestionsAnswersRoute
import com.vishal.interviewprepai.ui.screens.resume.ResumeUploadRoute
import com.vishal.interviewprepai.ui.screens.splash.SplashRoute
import kotlinx.coroutines.launch

@Composable
fun InterviewPrepApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Destinations.Splash.route,
    ) {
        composable(Destinations.Splash.route) {
            SplashRoute(
                onFinished = { navigateToHome ->
                    val target = if (navigateToHome) Destinations.Home.route else Destinations.Login.route
                    navController.navigate(target) {
                        popUpTo(Destinations.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Destinations.Login.route) {
            LoginRoute(
                onLoginSuccess = {
                    navController.navigate(Destinations.Home.route) {
                        popUpTo(Destinations.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Destinations.Home.route) {
            HomeRoute(
                onResumeUpload = { navController.navigate(Destinations.ResumeUpload.route) },
                onQna = { navController.navigate(Destinations.Qna.route) },
                onMockInterview = { navController.navigate(Destinations.MockInterview.route) },
                onLogout = {
                    scope.launch {
                        AppContainer.interviewRepository(context.applicationContext).logoutCurrentUser()
                        navController.navigate(Destinations.Login.route) {
                            popUpTo(Destinations.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(Destinations.ResumeUpload.route) {
            ResumeUploadRoute(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Destinations.Qna.route) },
            )
        }
        composable(Destinations.Qna.route) {
            QuestionsAnswersRoute(
                onBack = { navController.popBackStack() },
                onStartMock = { navController.navigate(Destinations.MockInterview.route) },
            )
        }
        composable(Destinations.MockInterview.route) {
            AiMockInterviewRoute(
                onBack = { navController.popBackStack() },
                onFinish = { navController.navigate(Destinations.Feedback.route) },
            )
        }
        composable(Destinations.Feedback.route) {
            FeedbackRoute(
                onBackHome = {
                    navController.navigate(Destinations.Home.route) {
                        popUpTo(Destinations.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
