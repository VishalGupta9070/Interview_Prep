package com.vishal.interviewprepai.ui.screens.qna

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishal.interviewprepai.data.di.AppContainer
import com.vishal.interviewprepai.domain.model.DifficultyLevel
import com.vishal.interviewprepai.ui.components.LoadingIndicator
import com.vishal.interviewprepai.ui.components.Pill
import com.vishal.interviewprepai.ui.components.PrimaryButton
import com.vishal.interviewprepai.ui.theme.Dimens
import com.vishal.interviewprepai.ui.util.viewModelFactory
import com.vishal.interviewprepai.viewmodel.qna.QnaViewModel

@Composable
fun QuestionsAnswersRoute(
    onBack: () -> Unit,
    onStartMock: () -> Unit,
) {
    val context = LocalContext.current
    val vm: QnaViewModel = viewModel(factory = viewModelFactory { QnaViewModel(AppContainer.interviewRepository(context)) })
    val state by vm.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.screenHorizontal)
                .padding(top = Dimens.screenTop),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap12),
        ) {
            item("top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_previous),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            item("title") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
                    Text(
                        text = "Interview Preparation",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "One scroll, multiple section types. Review HR, easy, medium, and hard preparation rounds in sequence.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            items(
                items = state.items,
                key = { item -> item.stableKey },
            ) { item ->
                when (item) {
                    is HrHeaderItem -> HrHeader(item)
                    is HrQuestionItem -> HrQuestionCard(
                        item = item,
                        expanded = state.expandedId == item.id,
                        onToggle = { vm.toggle(item.id) },
                    )
                    is TechnicalEasyHeaderItem -> TechnicalEasyHeader(item)
                    is TechnicalEasyItem -> TechnicalEasyCard(
                        item = item,
                        expanded = state.expandedId == item.id,
                        onToggle = { vm.toggle(item.id) },
                    )
                    is TechnicalMediumHeaderItem -> TechnicalMediumHeader(item)
                    is TechnicalMediumItem -> TechnicalMediumCard(
                        item = item,
                        expanded = state.expandedId == item.id,
                        onToggle = { vm.toggle(item.id) },
                    )
                    is TechnicalHardHeaderItem -> TechnicalHardHeader(item)
                    is TechnicalHardItem -> TechnicalHardCard(
                        item = item,
                        expanded = state.expandedId == item.id,
                        onToggle = { vm.toggle(item.id) },
                    )
                    is LoadingItem -> LoadingState(item.message)
                    is EmptyStateItem -> EmptyState(item.message)
                }
            }

            if (state.isLoading && state.questions.isNotEmpty()) {
                item("loading_more") {
                    LoadingState("Generating new questions across all four sections...")
                }
            }

            item("generate_more") {
                PrimaryButton(
                    text = if (state.isLoading && state.questions.isNotEmpty()) "Generating More..." else "Generate More",
                    onClick = vm::generateMore,
                    enabled = !state.isLoading,
                )
            }

            item("start_mock") {
                PrimaryButton(
                    text = "Start Mock Interview",
                    onClick = onStartMock,
                    enabled = state.questions.isNotEmpty() && !state.isLoading,
                )
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(Dimens.gap24))
            }
        }
    }
}

@Composable
private fun HrHeader(
    item: HrHeaderItem,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarBubble(
                iconRes = android.R.drawable.sym_action_chat,
                container = MaterialTheme.colorScheme.tertiary,
                content = MaterialTheme.colorScheme.onTertiary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionBadge(
                text = "HR",
                container = MaterialTheme.colorScheme.tertiary,
                content = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

@Composable
private fun TechnicalEasyHeader(
    item: TechnicalEasyHeaderItem,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        HeaderContent(
            iconRes = android.R.drawable.ic_menu_info_details,
            title = item.title,
            subtitle = item.subtitle,
            badgeText = "Easy",
            badgeContainer = MaterialTheme.colorScheme.primary,
            badgeContent = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun TechnicalMediumHeader(
    item: TechnicalMediumHeaderItem,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
    ) {
        HeaderContent(
            iconRes = android.R.drawable.ic_menu_manage,
            title = item.title,
            subtitle = item.subtitle,
            badgeText = "Medium",
            badgeContainer = MaterialTheme.colorScheme.secondary,
            badgeContent = MaterialTheme.colorScheme.onSecondary,
            dense = true,
        )
    }
}

@Composable
private fun TechnicalHardHeader(
    item: TechnicalHardHeaderItem,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        HeaderContent(
            iconRes = android.R.drawable.ic_dialog_alert,
            title = item.title,
            subtitle = item.subtitle,
            badgeText = "Hard",
            badgeContainer = MaterialTheme.colorScheme.errorContainer,
            badgeContent = MaterialTheme.colorScheme.onErrorContainer,
            emphasize = true,
        )
    }
}

@Composable
private fun HrQuestionCard(
    item: HrQuestionItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AvatarBubble(
                    iconRes = android.R.drawable.ic_dialog_email,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        SectionBadge(
                            text = "Behavioral",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    item.domain?.takeIf { it.isNotBlank() }?.let { domain ->
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ExpandableAnswer(
                answer = item.answer,
                expanded = expanded,
                label = "Suggested HR answer",
                labelColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun TechnicalEasyCard(
    item: TechnicalEasyItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SectionBadge(
                    text = item.difficulty.name,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            }
            item.domain?.takeIf { it.isNotBlank() }?.let { domain ->
                Pill(text = domain)
            }
            ExpandableAnswer(
                answer = item.answer,
                expanded = expanded,
                label = "Suggested answer",
                labelColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TechnicalMediumCard(
    item: TechnicalMediumItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    item.domain?.takeIf { it.isNotBlank() }?.let { domain ->
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SectionBadge(
                    text = item.difficulty.name,
                    container = MaterialTheme.colorScheme.secondary,
                    content = MaterialTheme.colorScheme.onSecondary,
                )
            }
            ExpandableAnswer(
                answer = item.answer,
                expanded = expanded,
                label = "Structured answer",
                labelColor = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun TechnicalHardCard(
    item: TechnicalHardItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .animateContentSize()
            .clickable(onClick = onToggle),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    item.domain?.takeIf { it.isNotBlank() }?.let { domain ->
                        Text(
                            text = "Focus: $domain",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SectionBadge(
                    text = "Advanced",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            ExpandableAnswer(
                answer = item.answer,
                expanded = expanded,
                label = "Advanced answer strategy",
                labelColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HeaderContent(
    iconRes: Int,
    title: String,
    subtitle: String?,
    badgeText: String,
    badgeContainer: Color,
    badgeContent: Color,
    dense: Boolean = false,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBubble(
            iconRes = iconRes,
            container = badgeContainer.copy(alpha = if (emphasize) 0.95f else 0.82f),
            content = badgeContent,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionBadge(
            text = badgeText,
            container = badgeContainer,
            content = badgeContent,
        )
    }
}

@Composable
private fun AvatarBubble(
    iconRes: Int,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
        )
    }
}

@Composable
private fun SectionBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ExpandableAnswer(
    answer: String,
    expanded: Boolean,
    label: String,
    labelColor: Color,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LoadingState(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gap24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gap24),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
