package com.vishal.interviewprepai.ui.screens.chat

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vishal.interviewprepai.data.di.AppContainer
import com.vishal.interviewprepai.ui.components.LoadingIndicator
import com.vishal.interviewprepai.ui.components.Pill
import com.vishal.interviewprepai.ui.components.PrimaryButton
import com.vishal.interviewprepai.ui.theme.Dimens
import com.vishal.interviewprepai.ui.util.viewModelFactory
import com.vishal.interviewprepai.viewmodel.chat.AiMockInterviewViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "AiMockInterviewRoute"

@Composable
fun AiMockInterviewRoute(
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val vm: AiMockInterviewViewModel = viewModel(
        factory = viewModelFactory {
            AiMockInterviewViewModel(
                repository = AppContainer.interviewRepository(context),
            )
        },
    )
    val state by vm.state.collectAsState()
    val isListening by vm.isListening.collectAsState()
    val speechText by vm.speechText.collectAsState()
    val currentQuestionText by vm.currentQuestionText.collectAsState()
    val isTtsEnabled by vm.isTtsEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isTtsAvailable by remember { mutableStateOf(false) }
    var lastSpokenQuestionId by remember { mutableStateOf<String?>(null) }

    val ttsController = remember(context) {
        QuestionTextToSpeechController(context) { available ->
            isTtsAvailable = available
        }
    }

    DisposableEffect(ttsController) {
        onDispose {
            ttsController.stop()
            ttsController.shutdown()
        }
    }

    LaunchedEffect(state.currentQuestion?.id, currentQuestionText, isTtsEnabled, isTtsAvailable) {
        val questionId = state.currentQuestion?.id ?: return@LaunchedEffect
        if (!isTtsEnabled || !isTtsAvailable || currentQuestionText.isBlank()) return@LaunchedEffect
        if (lastSpokenQuestionId == questionId) return@LaunchedEffect

        delay(350)
        ttsController.speakQuestion(
            questionText = currentQuestionText,
            utteranceId = questionId,
        )
        lastSpokenQuestionId = questionId
    }

    fun replayQuestion() {
        if (!isTtsAvailable || !isTtsEnabled || currentQuestionText.isBlank()) return
        ttsController.speakQuestion(
            questionText = currentQuestionText,
            utteranceId = state.currentQuestion?.id ?: "QUESTION_ID_REPLAY",
        )
    }

    val speechResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        vm.setListening(false)
        if (result.resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Speech dialog cancelled or failed: resultCode=${result.resultCode}")
            scope.launch {
                snackbarHostState.showSnackbar("Speech input was cancelled.")
            }
            return@rememberLauncherForActivityResult
        }

        val recognizedText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        Log.d(TAG, "Speech result received: hasText=${recognizedText.isNotBlank()}")
        if (recognizedText.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("No speech detected. Try again.")
            }
        } else {
            vm.appendSpeechText(recognizedText)
        }
    }

    fun launchSpeechIntent() {
        val hostActivity = activity
        if (hostActivity == null) {
            Log.e(TAG, "Unable to launch speech intent: Activity context is null")
            vm.setListening(false)
            scope.launch {
                snackbarHostState.showSnackbar("Unable to access the current activity for voice input.")
            }
            return
        }

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your answer")
        }

        val canHandleIntent = speechIntent.resolveActivity(hostActivity.packageManager) != null
        Log.d(TAG, "Launching speech intent: canHandleIntent=$canHandleIntent")
        if (!canHandleIntent) {
            vm.setListening(false)
            scope.launch {
                snackbarHostState.showSnackbar("No speech recognition service is available on this device.")
            }
            return
        }

        try {
            vm.setListening(true)
            speechResultLauncher.launch(speechIntent)
        } catch (error: ActivityNotFoundException) {
            vm.setListening(false)
            Log.e(TAG, "Speech activity not found", error)
            scope.launch {
                snackbarHostState.showSnackbar("No speech recognition app is installed.")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Log.d(TAG, "Permission result: granted=$granted")
        if (granted) {
            Log.d(TAG, "Permission granted")
            launchSpeechIntent()
        } else {
            vm.setListening(false)
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice input.")
            }
        }
    }

    fun handleMicClick() {
        Log.d(TAG, "Mic clicked")
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            Log.d(TAG, "Permission granted")
            launchSpeechIntent()
        } else {
            vm.setListening(false)
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    text = "AI Mock Interview",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            if (state.isLoading) {
                LoadingIndicator(modifier = Modifier.fillMaxSize())
            } else if (state.error != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap12)) {
                    Text(
                        text = state.error ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PrimaryButton(
                        text = "Retry",
                        onClick = { vm.startInterview() },
                    )
                }
            } else {
                val resumeData = state.resumeData
                val currentQuestion = state.currentQuestion
                val focusSkill = currentQuestion?.focusArea
                val completedStageSummary = state.questions
                    .map { it.stage.displayName }
                    .distinct()
                    .joinToString(", ")

                Text(
                    text = "${resumeData?.profession ?: "Software Developer"}  |  ${resumeData?.yearsOfExperience ?: 0} years",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Domain: ${resumeData?.primaryDomain ?: "General"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
                        Pill(text = state.progressLabel)
                        if (state.currentStageLabel.isNotBlank()) {
                            Pill(text = state.currentStageLabel)
                        }
                    }
                    focusSkill?.takeIf { it.isNotBlank() }?.let { skill ->
                        Pill(text = skill)
                    }
                }

                state.stageTransitionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (state.isCompleted || currentQuestion == null) {
                    Text(
                        text = "Interview complete. You made it through the full structured flow.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "You answered ${state.totalQuestionsAsked} questions across $completedStageSummary.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(
                        text = "Generate Feedback",
                        onClick = onFinish,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = currentQuestion.question,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = ::replayQuestion,
                            enabled = isTtsAvailable && isTtsEnabled && currentQuestionText.isNotBlank(),
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_lock_silent_mode_off),
                                contentDescription = "Replay question",
                                tint = if (isTtsEnabled && isTtsAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                        }
                        IconButton(
                            onClick = {
                                vm.toggleTtsEnabled()
                                if (isTtsEnabled) {
                                    ttsController.stop()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isTtsEnabled) {
                                        android.R.drawable.ic_lock_silent_mode_off
                                    } else {
                                        android.R.drawable.ic_lock_silent_mode
                                    },
                                ),
                                contentDescription = if (isTtsEnabled) "Mute question audio" else "Unmute question audio",
                                tint = if (isTtsEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.currentAnswer,
                        onValueChange = vm::onAnswerChange,
                        label = { Text("Your answer") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        trailingIcon = {
                            IconButton(onClick = ::handleMicClick) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isListening) {
                                            android.R.drawable.ic_media_pause
                                        } else {
                                            android.R.drawable.ic_btn_speak_now
                                        },
                                    ),
                                    contentDescription = if (isListening) "Speech dialog active" else "Start voice input",
                                    tint = if (isListening) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        supportingText = {
                            when {
                                isListening -> Text("Listening...")
                                speechText.isNotBlank() -> Text("Voice input added. You can edit or speak again.")
                                !isTtsEnabled -> Text("Question audio is muted.")
                            }
                        },
                    )
                    PrimaryButton(
                        text = if (state.totalQuestionsAsked + 1 >= state.totalQuestions) "Finish Interview" else "Next Question",
                        onClick = vm::getNextQuestion,
                        enabled = state.currentAnswer.isNotBlank(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gap12))
        }
    }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
