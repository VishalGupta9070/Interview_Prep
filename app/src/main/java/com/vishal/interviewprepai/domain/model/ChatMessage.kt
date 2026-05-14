package com.vishal.interviewprepai.domain.model

import com.vishal.interviewprepai.ui.components.ChatSender

data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
)

