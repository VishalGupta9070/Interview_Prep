package com.vishal.interviewprepai.ui.screens.chat

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishal.interviewprepai.data.di.AppContainer
import com.vishal.interviewprepai.ui.components.ChatBubble
import com.vishal.interviewprepai.ui.components.ChatInputField
import com.vishal.interviewprepai.ui.components.LoadingIndicator
import com.vishal.interviewprepai.ui.components.PrimaryButton
import com.vishal.interviewprepai.ui.theme.Dimens
import com.vishal.interviewprepai.ui.util.viewModelFactory
import com.vishal.interviewprepai.viewmodel.chat.MockInterviewViewModel

@Composable
fun MockInterviewRoute(
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val vm: MockInterviewViewModel = viewModel(factory = viewModelFactory { MockInterviewViewModel(AppContainer.interviewRepository(context)) })
    val state by vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.screenHorizontal)
                .padding(top = Dimens.screenTop),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap16),
        ) {
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
                Text(
                    text = "Mock Interview",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onFinish) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_save),
                        contentDescription = "Finish",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            if (state.isLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gap12),
                    reverseLayout = false,
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        ChatBubble(sender = msg.sender, text = msg.text)
                    }
                    item { Spacer(modifier = Modifier.height(Dimens.gap8)) }
                }

                ChatInputField(
                    value = state.input,
                    onValueChange = vm::onInputChange,
                    onSend = vm::send,
                )

                Spacer(modifier = Modifier.height(Dimens.gap8))

                PrimaryButton(
                    text = "Generate Feedback",
                    onClick = onFinish,
                )

                Spacer(modifier = Modifier.height(Dimens.gap12))
            }
        }
    }
}

