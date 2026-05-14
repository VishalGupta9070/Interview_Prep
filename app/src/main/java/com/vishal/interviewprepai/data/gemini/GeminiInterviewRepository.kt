package com.vishal.interviewprepai.data.gemini

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vishal.interviewprepai.data.interview.QuestionFlowEngine
import com.vishal.interviewprepai.data.interview.ResumeAnalyzer
import com.vishal.interviewprepai.data.local.dao.QuestionDao
import com.vishal.interviewprepai.data.local.dao.ResumeDao
import com.vishal.interviewprepai.data.local.entity.QuestionEntity
import com.vishal.interviewprepai.data.local.entity.ResumeEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.vishal.interviewprepai.data.resume.PdfTextExtractor
import com.vishal.interviewprepai.data.resume.ResumeTextCleaner
import com.vishal.interviewprepai.data.session.SessionManager
import com.vishal.interviewprepai.domain.model.ChatMessage
import com.vishal.interviewprepai.domain.model.DifficultyLevel
import com.vishal.interviewprepai.domain.model.FeedbackSummary
import com.vishal.interviewprepai.domain.model.QuestionAnswer
import com.vishal.interviewprepai.domain.model.RoundType
import com.vishal.interviewprepai.domain.model.interview.InterviewAnswer
import com.vishal.interviewprepai.domain.model.interview.InterviewConfig
import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.InterviewStage
import com.vishal.interviewprepai.domain.model.interview.InterviewStagePlan
import com.vishal.interviewprepai.domain.model.interview.MockInterviewSession
import com.vishal.interviewprepai.domain.model.interview.QuestionDifficulty
import com.vishal.interviewprepai.domain.model.interview.ResumeData
import com.vishal.interviewprepai.domain.repository.InterviewRepository
import com.vishal.interviewprepai.ui.components.ChatSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.util.UUID

class GeminiInterviewRepository(
    private val context: Context,
    private val api: GeminiApi,
    private val apiKey: String,
    private val sessionManager: SessionManager,
    private val resumeDao: ResumeDao,
    private val questionDao: QuestionDao,
    private val resumeAnalyzer: ResumeAnalyzer = ResumeAnalyzer(),
    private val questionFlowEngine: QuestionFlowEngine = QuestionFlowEngine(),
    private val model: String = "gemini-2.5-flash",
) : InterviewRepository {

    private val tag = "GeminiRepo"
    private val busyMessage = "AI service is busy, please try again"

    private val fallbackModels = listOf(
        // Primary (latest alias from ListModels)
        "models/gemini-flash-latest",
        // Fallbacks (from ListModels)
        "models/gemini-2.5-flash",
        "models/gemini-2.0-flash",
        "models/gemini-2.0-flash-lite",
    )

    private val retryDelayMs = 1500L
    private val perModelAttempts = 2 // retry each model 2 times total

    // Keep prompts bounded to reduce 503/latency and avoid huge payloads.
    private val maxResumeCharsForPrompt = 8_000
    private val maxPromptChars = 12_000

    @Volatile
    private var cachedQuestions: List<QuestionAnswer> = emptyList()

    @Volatile
    private var lastResumeText: String? = null

    @Volatile
    private var cachedConversation: List<ChatMessage> = emptyList()

    @Volatile
    private var detectedProfession: String? = null

    @Volatile
    private var resolvedModelPath: String? = null

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun getSuggestedQuestions(): List<QuestionAnswer> {
        if (cachedQuestions.isNotEmpty()) return cachedQuestions
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isBlank()) return emptyList()

        val dbQuestions = runCatching { questionDao.getByUserPhone(userPhone) }
            .getOrElse {
                Log.e(tag, "getSuggestedQuestions: failed to load from DB", it)
                emptyList()
            }
        val latestResume = runCatching { resumeDao.getLatestByUserPhone(userPhone) }.getOrNull()
        val fallbackDomain = latestResume
            ?.let { resume ->
                runCatching {
                    resumeAnalyzer.analyze(
                        resumeText = resume.extractedText,
                        professionHint = resume.profession,
                    ).primaryDomain
                }.getOrNull()
            }
        if (latestResume != null) {
            if (lastResumeText.isNullOrBlank()) lastResumeText = latestResume.extractedText
            if (detectedProfession.isNullOrBlank()) detectedProfession = latestResume.profession
            Log.d(tag, "getSuggestedQuestions: hydrated resume/profession from DB")
        }
        cachedQuestions = dbQuestions.map { item ->
            item.toQuestionAnswer(fallbackDomain = fallbackDomain)
        }
        Log.d(tag, "getSuggestedQuestions: loadedFromDb count=${cachedQuestions.size}")
        return cachedQuestions
    }

    override suspend fun generateQuestionsFromResume(pdfUri: Uri): List<QuestionAnswer> {
        Log.d(tag, "generateQuestionsFromResume: start")
        val resumeText = withContext(Dispatchers.IO) {
            val raw = PdfTextExtractor.extractText(context.contentResolver, pdfUri)
            ResumeTextCleaner.clean(raw)
        }
        if (resumeText.isBlank()) error("Empty resume text extracted from PDF")
        Log.d(tag, "generateQuestionsFromResume: extractedResumeChars=${resumeText.length}")

        lastResumeText = resumeText
        val professionHint = existingProfessionForCurrentUser() ?: detectProfessionFromResume(resumeText)
        val resumeData = resumeAnalyzer.analyze(
            resumeText = resumeText,
            professionHint = professionHint,
        )
        detectedProfession = resumeData.profession
        Log.d(tag, "generateQuestionsFromResume: detectedProfession=${resumeData.profession}")
        persistResume(resumeText, resumeData.profession)
        cachedConversation = emptyList()

        val items = generateQuestions(resumeText, resumeData)
        cachedQuestions = items
        Log.d(tag, "generateQuestionsFromResume: success items=${items.size}")
        return items
    }

    override suspend fun generateMoreAdvancedQuestions(): List<QuestionAnswer> {
        Log.d(tag, "generateMoreAdvancedQuestions: start")
        if (cachedQuestions.isEmpty()) {
            cachedQuestions = getSuggestedQuestions()
        }
        val resumeText = lastResumeText
        if (resumeText.isNullOrBlank()) {
            // If user lands directly on QnA screen without generating, don't crash; just return what's cached.
            return cachedQuestions
        }

        val resumeData = resumeAnalyzer.analyze(
            resumeText = resumeText,
            professionHint = detectedProfession.orEmpty().ifBlank { existingProfessionForCurrentUser().orEmpty() },
        )
        detectedProfession = resumeData.profession

        val config = buildGenerateMoreConfig(resumeData)
        val plan = questionFlowEngine.buildPlan(
            resume = resumeData,
            config = config,
        )
        val more = generatePreparationQuestions(
            resumeData = resumeData,
            config = config,
            plan = plan,
            existingQuestions = cachedQuestions,
        )

        persistQuestions(more)
        cachedQuestions = cachedQuestions + more
        Log.d(tag, "generateMoreAdvancedQuestions: appended=${more.size} total=${cachedQuestions.size}")
        return cachedQuestions
    }

    override suspend fun startStructuredMockInterview(
        resumeText: String?,
    ): MockInterviewSession {
        Log.d(tag, "startStructuredMockInterview: start")
        val sourceResume = resolveResumeText(resumeText)
        val professionHint = detectedProfession.orEmpty().ifBlank { existingProfessionForCurrentUser().orEmpty() }
        val resumeData = resumeAnalyzer.analyze(
            resumeText = sourceResume,
            professionHint = professionHint,
        )
        detectedProfession = resumeData.profession

        val config = questionFlowEngine.buildConfig(
            resume = resumeData,
        )
        val plan = questionFlowEngine.buildPlan(
            resume = resumeData,
            config = config,
        )
        val orderedQuestions = mutableListOf<InterviewQuestion>()

        InterviewStage.values().forEach { stage ->
            val stagePlan = plan.stagePlan(stage) ?: return@forEach
            if (stagePlan.questionCount <= 0) return@forEach

            val request = questionFlowEngine.buildStageRequest(
                stagePlan = stagePlan,
                resume = resumeData,
                existingQuestions = orderedQuestions,
            )
            val prompt = clampForPrompt(
                request.prompt,
                maxPromptChars,
                label = "prompt(mockInterview:${stage.name.lowercase()})",
            )
            val rawResponse = runCatching { callGemini(prompt, allowBusyMessage = true) }
                .onFailure { t -> Log.e(tag, "startStructuredMockInterview: stage=$stage failed", t) }
                .getOrDefault("")

            val parsedQuestions = questionFlowEngine.parseStageQuestions(
                stagePlan = stagePlan,
                rawResponse = rawResponse,
                expectedCount = stagePlan.questionCount,
            )
            val uniqueStageQuestions = parsedQuestions
                .filterNot { candidate -> orderedQuestions.any { it.question.sameQuestionAs(candidate.question) } }
                .take(stagePlan.questionCount)

            orderedQuestions += uniqueStageQuestions

            val remainingCount = stagePlan.questionCount - uniqueStageQuestions.size
            if (remainingCount > 0) {
                Log.w(tag, "startStructuredMockInterview: stage=$stage supplementing=$remainingCount")
                orderedQuestions += questionFlowEngine.supplementStageQuestions(
                    stagePlan = stagePlan,
                    resume = resumeData,
                    existingQuestions = orderedQuestions,
                    requiredCount = remainingCount,
                    config = config,
                )
            }
        }

        val session = MockInterviewSession(
            resumeData = resumeData,
            config = config,
            plan = plan,
            questions = orderedQuestions,
        )
        cachedConversation = emptyList()
        Log.d(
            tag,
            "startStructuredMockInterview: totalQuestions=${session.questions.size} expected=${plan.totalQuestions}",
        )
        return session
    }

    override fun cacheStructuredMockInterviewTranscript(
        resumeData: ResumeData,
        answers: List<InterviewAnswer>,
    ) {
        cachedConversation = answers.flatMap { answer ->
            listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = ChatSender.AI,
                    text = "[${answer.question.stage.displayName}] ${answer.question.question}",
                ),
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = ChatSender.USER,
                    text = answer.answer,
                ),
            )
        }
        detectedProfession = resumeData.profession
        lastResumeText = resumeData.resumeText
    }

    override suspend fun logoutCurrentUser() {
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isNotBlank()) {
            runCatching {
                // Safe order: delete user-scoped DB data first.
                questionDao.deleteByUserPhone(userPhone)
                resumeDao.deleteByUserPhone(userPhone)
            }.onFailure { t ->
                Log.e(tag, "logoutCurrentUser: failed to delete user data", t)
            }
        }
        // Then clear session and in-memory user data.
        sessionManager.logout()
        cachedQuestions = emptyList()
        lastResumeText = null
        detectedProfession = null
        cachedConversation = emptyList()
    }

    /**
     * Requirement: suspend fun generateQuestions(resumeText: String): List<QuestionAnswer>
     */
    suspend fun generateQuestions(
        resumeText: String,
        resumeData: ResumeData = resumeAnalyzer.analyze(
            resumeText = resumeText,
            professionHint = detectedProfession,
        ),
    ): List<QuestionAnswer> {
        Log.d(tag, "generateQuestions: start resumeChars=${resumeText.length}")
        val config = questionFlowEngine.buildConfig(
            resume = resumeData,
        )
        val plan = questionFlowEngine.buildPlan(
            resume = resumeData,
            config = config,
        )
        val mapped = generatePreparationQuestions(
            resumeData = resumeData,
            config = config,
            plan = plan,
            existingQuestions = emptyList(),
        )
        persistQuestions(mapped)
        return mapped
    }

    override suspend fun startMockInterview(): List<ChatMessage> {
        Log.d(tag, "startMockInterview: start")
        val profession = detectedProfession.orEmpty().ifBlank { "candidate" }
        val resumeContext = clampForPrompt(lastResumeText.orEmpty(), 2500, label = "resume(mockInterviewSeed)")
        val prompt = """
            You are a professional interviewer.
            Candidate profession: $profession
            Ask one question at a time based on candidate's resume.
            Evaluate answers briefly and continue the interview.
            Start the interview now with your first question.

            Candidate resume context:
            $resumeContext
        """.trimIndent()

        val aiText = callGemini(clampForPrompt(prompt, maxPromptChars, label = "prompt(mockInterviewSeed)"), allowBusyMessage = true)
        val first = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = ChatSender.AI,
            text = aiText,
        )
        cachedConversation = listOf(first)
        Log.d(tag, "startMockInterview: first=${aiText.take(200)}")
        return cachedConversation
    }

    override suspend fun sendMockInterviewMessage(conversation: List<ChatMessage>): ChatMessage {
        Log.d(tag, "sendMockInterviewMessage: convoSize=${conversation.size}")
        if (conversation.isEmpty()) {
            return startMockInterview().last()
        }

        // Keep server-side cache for feedback screen later.
        cachedConversation = conversation

        val profession = detectedProfession.orEmpty().ifBlank { "candidate" }
        val resumeContext = clampForPrompt(lastResumeText.orEmpty(), 2500, label = "resume(mockInterviewSys)")
        val systemPrompt = """
            You are a professional interviewer.
            Candidate profession: $profession
            Ask one question at a time based on candidate's resume.
            Evaluate answers briefly and continue the interview.

            Candidate resume context:
            $resumeContext
        """.trimIndent()

        val contents = buildList {
            add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(clampForPrompt(systemPrompt, maxPromptChars, label = "prompt(mockInterviewSys)"))),
                ),
            )
            conversation.forEach { msg ->
                val role = if (msg.sender == ChatSender.USER) "user" else "model"
                add(GeminiContent(role = role, parts = listOf(GeminiPart(msg.text))))
            }
        }

        val req = GeminiRequest(contents = contents)
        val text = generateWithFallback(req, allowBusyMessage = true).trim()
        if (text.isBlank()) error("Gemini returned empty response")
        Log.d(tag, "sendMockInterviewMessage: ai=${text.take(240)}")

        val ai = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = ChatSender.AI,
            text = text,
        )
        cachedConversation = cachedConversation + ai
        return ai
    }

    private suspend fun callGemini(prompt: String, allowBusyMessage: Boolean = false): String {
        val req = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = prompt),
                    ),
                ),
            ),
        )

        return generateWithFallback(req, allowBusyMessage = allowBusyMessage)
    }

    /**
     * Robust fallback mechanism for model overload/high demand.
     * Retries on HTTP 503 and SocketTimeoutException by trying next model in priority list.
     */
    suspend fun generateWithFallback(request: GeminiRequest, allowBusyMessage: Boolean = false): String {
        val safeKey = apiKeyOrThrow()

        val modelsToTry = fallbackModels.distinct()

        var lastError: Throwable? = null

        for (modelPath in modelsToTry) {
            // Level 1: retry per model. Level 2: fallback to next model.
            for (attempt in 1..perModelAttempts) {
                Log.d(tag, "generateWithFallback: trying model=$modelPath attempt=$attempt/$perModelAttempts")
                try {
                    val res = api.generateContent(
                        model = modelPath,
                        apiKey = safeKey,
                        request = request,
                    )
                    val text = res.candidates
                        .firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        ?.trim()
                        .orEmpty()

                    if (text.isBlank()) error("Gemini returned empty response")
                    Log.d(tag, "generateWithFallback: success model=$modelPath chars=${text.length}")
                    resolvedModelPath = modelPath
                    return text
                } catch (e: HttpException) {
                    lastError = e
                    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                    Log.e(tag, "generateWithFallback: HTTP ${e.code()} model=$modelPath errorBody=${body?.take(400)}", e)

                    when (e.code()) {
                        404 -> {
                            Log.w(tag, "generateWithFallback: skipping model=$modelPath (404)")
                            break
                        }
                        503 -> {
                            // overload: retry same model, then fallback to next
                            if (attempt < perModelAttempts) {
                                Log.d(tag, "generateWithFallback: retrying model=$modelPath reason=503 delayMs=$retryDelayMs")
                                delay(retryDelayMs)
                            }
                        }
                        else -> {
                            // Unknown/other HTTP issues: switch model.
                            Log.w(tag, "generateWithFallback: switching model due to HTTP ${e.code()} model=$modelPath")
                            break
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    lastError = e
                    Log.w(tag, "generateWithFallback: timeout model=$modelPath", e)
                    if (attempt < perModelAttempts) {
                        Log.d(tag, "generateWithFallback: retrying model=$modelPath reason=timeout delayMs=$retryDelayMs")
                        delay(retryDelayMs)
                    }
                } catch (t: Throwable) {
                    lastError = t
                    Log.e(tag, "generateWithFallback: unknown error model=$modelPath; switching model", t)
                    break
                }
            }

            Log.d(tag, "generateWithFallback: switching to next model after model=$modelPath")
            delay(retryDelayMs)
        }

        Log.e(tag, "generateWithFallback: all models failed; returning busy message", lastError)
        return if (allowBusyMessage) busyMessage else error(busyMessage)
    }

    override suspend fun getFeedback(): FeedbackSummary {
        Log.d(tag, "getFeedback: start convoSize=${cachedConversation.size}")
        if (cachedConversation.isEmpty()) error("No conversation found. Start a mock interview first.")

        val transcript = cachedConversation.joinToString("\n") { msg ->
            val who = if (msg.sender == ChatSender.USER) "USER" else "INTERVIEWER"
            "$who: ${msg.text}"
        }

        val prompt = """
            Analyze this mock interview transcript and provide feedback.
            Include:
            - Score from 0 to 100
            - Strengths
            - Weaknesses
            - Suggestions for improvement

            Return format:
            Score: <number>
            Strengths:
            - ...
            Weaknesses:
            - ...
            Suggestions:
            - ...

            Conversation:
            $transcript
        """.trimIndent()

        val response = callGemini(clampForPrompt(prompt, maxPromptChars, label = "prompt(feedback)"))
        Log.d(tag, "getFeedback: rawResponse=${response.take(280)}")

        val (score, highlights, improvements) = parseFeedback(response)
        return FeedbackSummary(
            score = score,
            highlights = highlights,
            improvements = improvements,
        )
    }

    private fun parseFeedback(text: String): Triple<Int, List<String>, List<String>> {
        val lines = text.lines().map { it.trim() }
        val strengths = mutableListOf<String>()
        val weaknessesAndSuggestions = mutableListOf<String>()
        var score = 0

        var section: String? = null
        for (line in lines) {
            if (line.isBlank()) continue
            when {
                line.startsWith("score", ignoreCase = true) -> {
                    score = Regex("""(\d{1,3})""")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?.coerceIn(0, 100)
                        ?: score
                }
                line.contains("strength", ignoreCase = true) -> section = "strength"
                line.contains("weak", ignoreCase = true) -> section = "weak"
                line.contains("suggest", ignoreCase = true) -> section = "suggest"
                line.startsWith("-", true) || line.startsWith("•") -> {
                    val item = line.removePrefix("-").removePrefix("•").trim()
                    if (item.isBlank()) continue
                    when (section) {
                        "strength" -> strengths += item
                        "weak", "suggest" -> weaknessesAndSuggestions += item
                        else -> weaknessesAndSuggestions += item
                    }
                }
            }
        }

        val safeStrengths = if (strengths.isNotEmpty()) strengths else listOf("See full feedback response in logs.")
        val safeImprovements = if (weaknessesAndSuggestions.isNotEmpty()) weaknessesAndSuggestions else listOf("See full feedback response in logs.")
        return Triple(score, safeStrengths, safeImprovements)
    }

    private suspend fun detectProfessionFromResume(resumeText: String): String {
        val resumeContext = clampForPrompt(resumeText, maxResumeCharsForPrompt, label = "resume(profession)")
        val prompt = """
            Analyze this resume and return only the most likely profession title.
            Examples: Android Developer, Backend Engineer, Data Scientist, Product Manager.
            Return only one short title and nothing else.

            Resume:
            $resumeContext
        """.trimIndent()

        val raw = runCatching { callGemini(clampForPrompt(prompt, maxPromptChars, label = "prompt(profession)")) }.getOrDefault("")
        val title = raw
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace("*", "")
            .replace("\"", "")
            .trim()
            .removePrefix("-")
            .trim()
            .take(80)

        return if (title.isNotBlank()) title else "Software Engineer"
    }

    private suspend fun resolveResumeText(overrideText: String?): String {
        val inlineResume = overrideText?.trim().takeUnless { it.isNullOrBlank() }
        if (inlineResume != null) {
            lastResumeText = inlineResume
            return inlineResume
        }

        val cachedResume = lastResumeText?.trim().takeUnless { it.isNullOrBlank() }
        if (cachedResume != null) return cachedResume

        val storedResume = loadResumeTextForCurrentUser()?.trim().takeUnless { it.isNullOrBlank() }
        if (storedResume != null) {
            lastResumeText = storedResume
            return storedResume
        }

        val fallbackResume = "Software developer with experience building APIs, mobile features, and production-ready applications."
        lastResumeText = fallbackResume
        return fallbackResume
    }

    private suspend fun loadResumeTextForCurrentUser(): String? {
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isBlank()) return null
        return runCatching { resumeDao.getLatestByUserPhone(userPhone)?.extractedText }
            .onFailure { t -> Log.e(tag, "loadResumeTextForCurrentUser failed", t) }
            .getOrNull()
    }

    private suspend fun existingProfessionForCurrentUser(): String? {
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isBlank()) return null
        return runCatching { resumeDao.getLatestByUserPhone(userPhone)?.profession?.trim() }
            .onFailure { t -> Log.e(tag, "existingProfessionForCurrentUser failed", t) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun clampForPrompt(text: String, maxChars: Int, label: String): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        Log.w(tag, "clampForPrompt: label=$label trimmed ${trimmed.length} -> $maxChars chars")
        return trimmed.take(maxChars)
    }

    private suspend fun persistResume(extractedText: String, profession: String) {
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isBlank()) {
            Log.w(tag, "persistResume: skipped, missing userPhone in session")
            return
        }
        runCatching {
            resumeDao.insert(
                ResumeEntity(
                    userPhone = userPhone,
                    extractedText = extractedText,
                    profession = profession,
                ),
            )
        }.onFailure { t ->
            Log.e(tag, "persistResume failed", t)
        }
    }

    private suspend fun persistQuestions(items: List<QuestionAnswer>) {
        if (items.isEmpty()) return
        val userPhone = sessionManager.userPhone()?.trim().orEmpty()
        if (userPhone.isBlank()) {
            Log.w(tag, "persistQuestions: skipped, missing userPhone in session")
            return
        }
        runCatching {
            questionDao.insertAll(
                items.map { qa ->
                    QuestionEntity(
                        userPhone = userPhone,
                        question = qa.question,
                        answer = qa.suggestedAnswer,
                        roundType = qa.roundType?.name,
                        difficultyLevel = qa.difficultyLevel?.name,
                        domain = qa.domain,
                    )
                },
            )
        }.onFailure { t ->
            Log.e(tag, "persistQuestions failed", t)
        }
    }

    private fun buildGenerateMoreConfig(
        resumeData: ResumeData,
    ): InterviewConfig {
        val stageDistribution = when (resumeData.experienceBand) {
            com.vishal.interviewprepai.domain.model.interview.ExperienceBand.EARLY_CAREER ->
                linkedMapOf(
                    InterviewStage.HR to 2,
                    InterviewStage.TECH1 to 3,
                    InterviewStage.TECH2 to 3,
                    InterviewStage.TECH3 to 2,
                )
            com.vishal.interviewprepai.domain.model.interview.ExperienceBand.MID_LEVEL ->
                linkedMapOf(
                    InterviewStage.HR to 2,
                    InterviewStage.TECH1 to 2,
                    InterviewStage.TECH2 to 3,
                    InterviewStage.TECH3 to 3,
                )
            com.vishal.interviewprepai.domain.model.interview.ExperienceBand.SENIOR ->
                linkedMapOf(
                    InterviewStage.HR to 2,
                    InterviewStage.TECH1 to 1,
                    InterviewStage.TECH2 to 3,
                    InterviewStage.TECH3 to 4,
                )
        }

        return InterviewConfig(
            totalQuestions = stageDistribution.values.sum(),
            includeResumeQuestions = true,
            stageQuestionDistribution = stageDistribution,
        )
    }

    private suspend fun generatePreparationQuestions(
        resumeData: ResumeData,
        config: InterviewConfig,
        plan: com.vishal.interviewprepai.domain.model.interview.InterviewPlan,
        existingQuestions: List<QuestionAnswer>,
    ): List<QuestionAnswer> {
        val combinedQuestions = existingQuestions.toMutableList()
        val generatedQuestions = mutableListOf<QuestionAnswer>()

        InterviewStage.values().forEach { stage ->
            val stagePlan = plan.stagePlan(stage) ?: return@forEach
            if (stagePlan.questionCount <= 0) return@forEach

            val prompt = clampForPrompt(
                buildPreparationStagePrompt(
                    stagePlan = stagePlan,
                    resumeData = resumeData,
                    existingQuestions = combinedQuestions,
                ),
                maxPromptChars,
                label = "prompt(qna:${stage.name.lowercase()})",
            )
            val rawResponse = runCatching { callGemini(prompt) }
                .onFailure { t -> Log.e(tag, "generatePreparationQuestions: stage=$stage failed", t) }
                .getOrDefault("")

            val parsed = GeminiQaParser.parse(rawResponse)
                .map { (question, answer) ->
                    QuestionAnswer(
                        id = UUID.randomUUID().toString(),
                        question = question.trim(),
                        suggestedAnswer = answer.trim(),
                        roundType = stage.toRoundType(),
                        difficultyLevel = stagePlan.difficulty.toDifficultyLevel(),
                        domain = stagePlan.domain,
                    )
                }
                .filter { candidate ->
                    candidate.question.isNotBlank() &&
                        candidate.suggestedAnswer.isNotBlank() &&
                        combinedQuestions.none { it.question.sameQuestionAs(candidate.question) }
                }
                .distinctBy { normalizeQuestion(it.question) }
                .take(stagePlan.questionCount)

            generatedQuestions += parsed
            combinedQuestions += parsed

            val remainingCount = stagePlan.questionCount - parsed.size
            if (remainingCount <= 0) return@forEach

            val fallbackQuestions = questionFlowEngine.supplementStageQuestions(
                stagePlan = stagePlan,
                resume = resumeData,
                existingQuestions = combinedQuestions.toInterviewQuestions(),
                requiredCount = remainingCount,
                config = config,
            )

            val fallbackAnswers = generateAnswersForFallbackQuestions(
                stagePlan = stagePlan,
                resumeData = resumeData,
                fallbackQuestions = fallbackQuestions,
            )
                .filter { candidate ->
                    combinedQuestions.none { it.question.sameQuestionAs(candidate.question) }
                }
                .take(remainingCount)

            generatedQuestions += fallbackAnswers
            combinedQuestions += fallbackAnswers
        }

        return generatedQuestions
    }

    private fun buildPreparationStagePrompt(
        stagePlan: InterviewStagePlan,
        resumeData: ResumeData,
        existingQuestions: List<QuestionAnswer>,
    ): String {
        val skills = resumeData.allSkills.joinToString().ifBlank { resumeData.primaryDomain }
        val resumeContext = if (stagePlan.includeResumeDeepDive) {
            clampForPrompt(resumeData.resumeText, 3_500, label = "resume(qna:${stagePlan.stage.name.lowercase()})")
        } else {
            ""
        }

        return buildString {
            appendLine("You are preparing a structured interview preparation question bank with suggested answers.")
            appendLine("Candidate profession: ${resumeData.profession}")
            appendLine("Candidate primary domain: ${resumeData.primaryDomain}")
            appendLine("Candidate years of experience: ${resumeData.yearsOfExperience}")
            appendLine("Candidate skills: $skills")
            appendLine("Current preparation section: ${stagePlan.stage.displayName}")
            appendLine("Difficulty level: ${stagePlan.promptDifficultyLabel}")
            appendLine("Generate exactly ${stagePlan.questionCount} unique question-and-answer pairs.")
            when (stagePlan.stage) {
                InterviewStage.HR -> appendLine(
                    "Generate REAL HR interview questions only. Cover topics like tell me about yourself, why should we hire you, strengths and weaknesses, teamwork, conflict handling, leadership, career goals, salary expectations, work pressure, communication, and behavioral or situational HR questions. Do not include any technical questions.",
                )
                InterviewStage.TECH1 -> appendLine(
                    "For a ${resumeData.profession} with ${resumeData.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for ${stagePlan.stage.displayName}. Focus on $skills. Include real-world scenarios if applicable. Avoid duplicates.",
                )
                InterviewStage.TECH2 -> appendLine(
                    "For a ${resumeData.profession} with ${resumeData.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for ${stagePlan.stage.displayName}. Focus on $skills. Include practical usage, design understanding, and real-world scenarios if applicable. Avoid duplicates.",
                )
                InterviewStage.TECH3 -> appendLine(
                    "For a ${resumeData.profession} with ${resumeData.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for ${stagePlan.stage.displayName}. Focus on $skills. Include resume-based questions, project deep dives, optimization, debugging, system design, and real-world problems. Avoid duplicates.",
                )
            }
            appendLine("Each answer should be concise, practical, and strong enough for interview preparation.")
            appendLine("Format strictly as:")
            appendLine("Q1: ...")
            appendLine("A1: ...")
            if (existingQuestions.isNotEmpty()) {
                appendLine("Do not repeat any of these existing questions:")
                existingQuestions.forEach { question ->
                    appendLine("- ${question.question}")
                }
            }
            if (resumeContext.isNotBlank()) {
                appendLine("Resume context:")
                appendLine(resumeContext)
            }
        }
    }

    private suspend fun generateAnswersForFallbackQuestions(
        stagePlan: InterviewStagePlan,
        resumeData: ResumeData,
        fallbackQuestions: List<InterviewQuestion>,
    ): List<QuestionAnswer> {
        if (fallbackQuestions.isEmpty()) return emptyList()

        val prompt = clampForPrompt(
            buildFallbackAnswersPrompt(
                stagePlan = stagePlan,
                resumeData = resumeData,
                fallbackQuestions = fallbackQuestions,
            ),
            maxPromptChars,
            label = "prompt(qnaAnswers:${stagePlan.stage.name.lowercase()})",
        )
        val rawResponse = runCatching { callGemini(prompt) }
            .onFailure { t -> Log.e(tag, "generateAnswersForFallbackQuestions failed", t) }
            .getOrDefault("")

        val parsedAnswers = GeminiQaParser.parse(rawResponse)
            .map { it.second.trim() }

        return fallbackQuestions.mapIndexed { index, question ->
            QuestionAnswer(
                id = UUID.randomUUID().toString(),
                question = question.question,
                suggestedAnswer = parsedAnswers.getOrNull(index).takeUnless { it.isNullOrBlank() }
                    ?: genericSuggestedAnswer(question, resumeData),
                roundType = question.stage.toRoundType(),
                difficultyLevel = question.difficulty.toDifficultyLevel(),
                domain = question.domain,
            )
        }
    }

    private fun buildFallbackAnswersPrompt(
        stagePlan: InterviewStagePlan,
        resumeData: ResumeData,
        fallbackQuestions: List<InterviewQuestion>,
    ): String {
        return buildString {
            appendLine("You are preparing suggested interview answers for a ${resumeData.profession}.")
            appendLine("Candidate primary domain: ${resumeData.primaryDomain}")
            appendLine("Candidate years of experience: ${resumeData.yearsOfExperience}")
            appendLine("Current preparation section: ${stagePlan.stage.displayName}")
            appendLine("Provide a concise, practical, high-quality answer for each question below.")
            appendLine("Keep the order exactly the same.")
            appendLine("Format strictly as:")
            appendLine("Q1: <repeat question>")
            appendLine("A1: <answer>")
            fallbackQuestions.forEachIndexed { index, question ->
                appendLine("Q${index + 1}: ${question.question}")
            }
        }
    }

    private fun genericSuggestedAnswer(
        question: InterviewQuestion,
        resumeData: ResumeData,
    ): String {
        return when (question.stage) {
            InterviewStage.HR ->
                "Answer with a clear real example, explain your role, describe the action you took, and close with the result and what you learned."
            InterviewStage.TECH1 ->
                "Start with the core definition, explain why it matters in ${resumeData.primaryDomain}, and mention a simple example from your own work or learning."
            InterviewStage.TECH2 ->
                "Explain the practical approach you would take, include key implementation details, and mention trade-offs, testing, or debugging where relevant."
            InterviewStage.TECH3 ->
                "Structure your answer around the problem, constraints, architecture or debugging approach, trade-offs, and the outcome you would optimize for in production."
        }
    }

    private fun List<QuestionAnswer>.toInterviewQuestions(): List<InterviewQuestion> {
        return map { item ->
            val roundType = item.roundType ?: classifyRoundType(item.question)
            val difficulty = item.difficultyLevel ?: defaultDifficultyFor(roundType)
            InterviewQuestion(
                id = item.id,
                stage = roundType.toInterviewStage(),
                difficulty = difficulty.toQuestionDifficulty(),
                domain = item.domain ?: roundType.title,
                question = item.question,
            )
        }
    }

    private fun QuestionEntity.toQuestionAnswer(
        fallbackDomain: String?,
    ): QuestionAnswer {
        val resolvedRoundType = roundType?.toRoundTypeOrNull() ?: classifyRoundType(question)
        val resolvedDifficulty = difficultyLevel?.toDifficultyLevelOrNull() ?: defaultDifficultyFor(resolvedRoundType)
        return QuestionAnswer(
            id = id.toString(),
            question = question,
            suggestedAnswer = answer,
            roundType = resolvedRoundType,
            difficultyLevel = resolvedDifficulty,
            domain = domain ?: fallbackDomain,
        )
    }

    private fun classifyRoundType(question: String): RoundType {
        val normalized = question.lowercase()
        return when {
            normalized.contains("tell me about yourself") ||
                normalized.contains("why should we hire") ||
            normalized.contains("team") ||
            normalized.contains("strength") ||
            normalized.contains("weakness") ||
                normalized.contains("career") ||
                normalized.contains("conflict") ||
                normalized.contains("leader") ||
                normalized.contains("salary") ||
                normalized.contains("pressure") ||
                normalized.contains("communicat") ||
                normalized.contains("behavior") ||
                normalized.contains("situat") ||
                normalized.contains("motivat") ||
                normalized.contains("yourself") -> RoundType.HR

            normalized.contains("architecture") ||
                normalized.contains("system design") ||
                normalized.contains("scal") ||
                normalized.contains("optimization") ||
                normalized.contains("production") ||
                normalized.contains("incident") ||
                normalized.contains("trade-off") ||
                normalized.contains("debug") -> RoundType.TECHNICAL_3

            normalized.contains("implement") ||
                normalized.contains("practical") ||
                normalized.contains("api") ||
                normalized.contains("test") ||
                normalized.contains("design") ||
                normalized.contains("usage") -> RoundType.TECHNICAL_2

            else -> RoundType.TECHNICAL_1
        }
    }

    private fun defaultDifficultyFor(roundType: RoundType): DifficultyLevel {
        return when (roundType) {
            RoundType.HR,
            RoundType.TECHNICAL_1,
            -> DifficultyLevel.EASY
            RoundType.TECHNICAL_2 -> DifficultyLevel.MEDIUM
            RoundType.TECHNICAL_3 -> DifficultyLevel.HARD
        }
    }

    private fun String.toRoundTypeOrNull(): RoundType? {
        return runCatching { RoundType.valueOf(this) }.getOrNull()
    }

    private fun String.toDifficultyLevelOrNull(): DifficultyLevel? {
        return runCatching { DifficultyLevel.valueOf(this) }.getOrNull()
    }

    private fun InterviewStage.toRoundType(): RoundType {
        return when (this) {
            InterviewStage.HR -> RoundType.HR
            InterviewStage.TECH1 -> RoundType.TECHNICAL_1
            InterviewStage.TECH2 -> RoundType.TECHNICAL_2
            InterviewStage.TECH3 -> RoundType.TECHNICAL_3
        }
    }

    private fun RoundType.toInterviewStage(): InterviewStage {
        return when (this) {
            RoundType.HR -> InterviewStage.HR
            RoundType.TECHNICAL_1 -> InterviewStage.TECH1
            RoundType.TECHNICAL_2 -> InterviewStage.TECH2
            RoundType.TECHNICAL_3 -> InterviewStage.TECH3
        }
    }

    private fun QuestionDifficulty.toDifficultyLevel(): DifficultyLevel {
        return when (this) {
            QuestionDifficulty.EASY -> DifficultyLevel.EASY
            QuestionDifficulty.MEDIUM -> DifficultyLevel.MEDIUM
            QuestionDifficulty.HARD -> DifficultyLevel.HARD
        }
    }

    private fun DifficultyLevel.toQuestionDifficulty(): QuestionDifficulty {
        return when (this) {
            DifficultyLevel.EASY -> QuestionDifficulty.EASY
            DifficultyLevel.MEDIUM -> QuestionDifficulty.MEDIUM
            DifficultyLevel.HARD -> QuestionDifficulty.HARD
        }
    }

    private fun apiKeyOrThrow(): String {
        val key = apiKey.trim()
        if (key.isBlank()) error("Missing GEMINI_API_KEY (check local.properties)")
        return key
    }

    private suspend fun resolveGenerateContentModelPath(): String? {
        val safeKey = apiKeyOrThrow()
        Log.d(tag, "resolveGenerateContentModelPath: ListModels")
        val models = api.listModels(apiKey = safeKey).models
        val supported = models.filter { model ->
            model.supportedGenerationMethods.any { method ->
                method.contains("generateContent", ignoreCase = true)
            }
        }

        // Some accounts/regions can return incomplete method metadata.
        // If that happens, gracefully fall back to Gemini models by name.
        val candidates = if (supported.isNotEmpty()) supported else models.filter {
            it.name.contains("gemini", ignoreCase = true)
        }

        // Prefer current stable Flash families first, then any Gemini model.
        val preferred = candidates.firstOrNull { it.name.contains("gemini-2.5-flash", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("gemini-2.0-flash", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("flash", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("gemini", ignoreCase = true) }
            ?: candidates.firstOrNull()

        val chosen = preferred?.name?.takeIf { it.isNotBlank() }
        Log.d(
            tag,
            "resolveGenerateContentModelPath: chosen=${chosen ?: "none"} totalSupported=${supported.size} totalModels=${models.size}",
        )
        return chosen
    }

    private fun String.sameQuestionAs(other: String): Boolean {
        return normalizeQuestion(this) == normalizeQuestion(other)
    }

    private fun normalizeQuestion(value: String): String {
        return value
            .lowercase()
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
