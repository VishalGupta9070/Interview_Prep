package com.vishal.interviewprepai.ui.screens.feedback

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishal.interviewprepai.data.di.AppContainer
import com.vishal.interviewprepai.ui.components.LoadingIndicator
import com.vishal.interviewprepai.ui.components.PrimaryButton
import com.vishal.interviewprepai.ui.theme.Dimens
import com.vishal.interviewprepai.ui.util.viewModelFactory
import com.vishal.interviewprepai.viewmodel.feedback.FeedbackViewModel

@Composable
fun FeedbackRoute(
    onBackHome: () -> Unit,
) {
    val context = LocalContext.current
    val vm: FeedbackViewModel = viewModel(factory = viewModelFactory { FeedbackViewModel(AppContainer.interviewRepository(context)) })
    val state by vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.isLoading || state.summary == null) {
            LoadingIndicator(modifier = Modifier.fillMaxSize())
        } else {
            val summary = state.summary!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.screenHorizontal)
                    .padding(top = Dimens.screenTop),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap16),
            ) {
                Text(
                    text = "Feedback",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Here’s how you did in this mock interview.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.cardRadius))
                        .padding(Dimens.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Overall Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${summary.score}/100",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "Premium",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gap12),
                ) {
                    item {
                        SectionCard(
                            title = "Highlights",
                            items = summary.highlights,
                        )
                    }
                    item {
                        SectionCard(
                            title = "Areas to improve",
                            items = summary.improvements,
                        )
                    }
                    item { Spacer(modifier = Modifier.height(Dimens.gap8)) }
                }

                PrimaryButton(
                    text = "Back to Home",
                    onClick = onBackHome,
                )

                Spacer(modifier = Modifier.height(Dimens.gap12))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.cardRadius))
            .padding(Dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        items.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

