package com.vishal.interviewprepai.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishal.interviewprepai.data.repository.FakeFeatureRepository
import com.vishal.interviewprepai.ui.components.FeatureCard
import com.vishal.interviewprepai.ui.components.LoadingIndicator
import com.vishal.interviewprepai.ui.theme.Dimens
import com.vishal.interviewprepai.ui.util.viewModelFactory
import com.vishal.interviewprepai.viewmodel.home.HomeViewModel

@Composable
fun HomeRoute(
    onResumeUpload: () -> Unit,
    onQna: () -> Unit,
    onMockInterview: () -> Unit,
    onLogout: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(FakeFeatureRepository()) })
    val state by vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.fillMaxSize())
        } else {
            HomeScreen(
                onResumeUpload = onResumeUpload,
                onQna = onQna,
                onMockInterview = onMockInterview,
                onLogout = onLogout,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onResumeUpload: () -> Unit,
    onQna: () -> Unit,
    onMockInterview: () -> Unit,
    onLogout: () -> Unit,
) {
    val items = listOf(
        Triple("Resume → Questions", onResumeUpload, "Recommended"),
        Triple("Questions & Answers", onQna, "Practice"),
        Triple("Mock Interview", onMockInterview, "Live"),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenHorizontal)
            .padding(top = Dimens.screenTop),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap16),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(modifier = Modifier.height(Dimens.gap20))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Your AI Interview Coach",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
                Text(
                    text = "Pick a mode to start preparing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Dimens.gap12))
            }
        }

        items(items) { (title, onClick, badge) ->
            FeatureCard(
                title = title,
                subtitle = when (title) {
                    "Resume → Questions" -> "Upload your resume and get tailored interview questions."
                    "Questions & Answers" -> "Review curated questions with suggested answers."
                    else -> "Chat-based mock interview with an AI coach."
                },
                badge = badge,
                onClick = onClick,
            )
        }

        item { Spacer(modifier = Modifier.height(Dimens.gap24)) }
    }
}

