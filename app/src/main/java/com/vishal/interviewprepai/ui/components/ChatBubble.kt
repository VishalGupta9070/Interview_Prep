package com.vishal.interviewprepai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vishal.interviewprepai.ui.theme.Dimens

enum class ChatSender { AI, USER }

@Composable
fun ChatBubble(
    sender: ChatSender,
    text: String,
    modifier: Modifier = Modifier,
) {
    val isUser = sender == ChatSender.USER
    val rowAlignment = if (isUser) Arrangement.End else Arrangement.Start
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp,
    )

    val bubbleModifier = if (isUser) {
        Modifier.background(
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                ),
            ),
            shape = bubbleShape,
        )
    } else {
        Modifier.background(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = bubbleShape,
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = rowAlignment,
    ) {
        Column(
            modifier = bubbleModifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isUser) "You" else "AI Coach",
                style = MaterialTheme.typography.labelLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(Dimens.gap12))
    }
}

