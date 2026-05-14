package com.vishal.interviewprepai.data.interview

import com.google.gson.JsonParser
import com.vishal.interviewprepai.domain.model.interview.ExperienceBand
import com.vishal.interviewprepai.domain.model.interview.InterviewConfig
import com.vishal.interviewprepai.domain.model.interview.InterviewPlan
import com.vishal.interviewprepai.domain.model.interview.InterviewQuestion
import com.vishal.interviewprepai.domain.model.interview.InterviewStage
import com.vishal.interviewprepai.domain.model.interview.InterviewStagePlan
import com.vishal.interviewprepai.domain.model.interview.QuestionDifficulty
import com.vishal.interviewprepai.domain.model.interview.ResumeData
import java.util.UUID

class QuestionFlowEngine {

    fun buildQuestionSet(
        resume: ResumeData,
    ): List<InterviewQuestion> {
        val config = buildConfig(resume)
        val plan = buildPlan(
            resume = resume,
            config = config,
        )
        val orderedQuestions = mutableListOf<InterviewQuestion>()
        InterviewStage.values().forEach { stage ->
            val stagePlan = plan.stagePlan(stage) ?: return@forEach
            if (stagePlan.questionCount <= 0) return@forEach
            orderedQuestions += supplementStageQuestions(
                stagePlan = stagePlan,
                resume = resume,
                existingQuestions = orderedQuestions,
                requiredCount = stagePlan.questionCount,
                config = config,
            )
        }
        return orderedQuestions
    }

    fun buildConfig(
        resume: ResumeData,
    ): InterviewConfig {
        val distribution = when (resume.experienceBand) {
            ExperienceBand.EARLY_CAREER -> linkedStageMap(
                hrCount = 6,
                tech1Count = 10,
                tech2Count = 9,
                tech3Count = 8,
            )
            ExperienceBand.MID_LEVEL -> linkedStageMap(
                hrCount = 6,
                tech1Count = 9,
                tech2Count = 9,
                tech3Count = 10,
            )
            ExperienceBand.SENIOR -> linkedStageMap(
                hrCount = 5,
                tech1Count = 8,
                tech2Count = 9,
                tech3Count = 11,
            )
        }

        return InterviewConfig(
            totalQuestions = distribution.values.sum(),
            includeResumeQuestions = true,
            stageQuestionDistribution = distribution,
        )
    }

    fun buildPlan(
        resume: ResumeData,
        config: InterviewConfig,
    ): InterviewPlan {
        val stagePlans = InterviewStage.values().map { stage ->
            val profile = difficultyProfileFor(
                stage = stage,
                experienceBand = resume.experienceBand,
            )
            InterviewStagePlan(
                stage = stage,
                questionCount = config.stageCount(stage),
                difficulty = profile.difficulty,
                promptDifficultyLabel = profile.promptLabel,
                domain = domainForStage(stage, resume),
                includeResumeDeepDive = config.includeResumeQuestions && stage == InterviewStage.TECH3,
            )
        }
        return InterviewPlan(stagePlans = stagePlans)
    }

    fun buildStageRequest(
        stagePlan: InterviewStagePlan,
        resume: ResumeData,
        existingQuestions: List<InterviewQuestion>,
    ): StageGenerationRequest {
        val focusAreas = focusAreasFor(
            resume = resume,
            stage = stagePlan.stage,
        )
        val prompt = buildString {
            appendLine("You are simulating a structured four-round job interview.")
            appendLine("The round order is fixed: HR, Technical Round 1, Technical Round 2, Technical Round 3.")
            appendLine("Candidate profession: ${resume.profession}")
            appendLine("Candidate primary domain: ${resume.primaryDomain}")
            appendLine("Candidate years of experience: ${resume.yearsOfExperience}")
            appendLine("Candidate primary skills: ${resume.primarySkills.joinToString().ifBlank { "None identified" }}")
            appendLine("Candidate secondary skills: ${resume.secondarySkills.joinToString().ifBlank { "None identified" }}")
            appendLine("Current stage: ${stagePlan.stage.name}")
            appendLine("Current round label: ${stagePlan.stage.displayName}")
            appendLine("Difficulty level: ${stagePlan.promptDifficultyLabel}")
            appendLine("Question domain: ${stagePlan.domain}")
            appendLine("Focus areas: ${focusAreas.joinToString()}")
            appendLine("Generate exactly ${stagePlan.questionCount} unique interview questions.")
            appendLine(stageInstruction(stagePlan, resume))
            appendLine(experienceInstruction(resume.experienceBand, stagePlan))
            appendLine("Ask only one question per item.")
            appendLine("Do not include answers.")
            appendLine("Do not use numbering, markdown, or commentary.")
            appendLine("Do not repeat or paraphrase previously asked questions.")
            if (existingQuestions.isNotEmpty()) {
                appendLine("Questions already used in earlier rounds:")
                existingQuestions.forEach { question ->
                    appendLine("- ${question.question}")
                }
            }
            if (stagePlan.includeResumeDeepDive) {
                appendLine("This final technical round must include resume-based questions, project deep dives, and real-world problems.")
                appendLine("Resume context:")
                appendLine(resume.resumeText.take(3_500))
            }
            appendLine("""Return ONLY valid JSON in this exact shape: {"questions":[{"question":"...","focusArea":"..."}]}""")
        }

        return StageGenerationRequest(
            stage = stagePlan.stage,
            count = stagePlan.questionCount,
            difficulty = stagePlan.difficulty,
            domain = stagePlan.domain,
            prompt = prompt,
        )
    }

    fun parseStageQuestions(
        stagePlan: InterviewStagePlan,
        rawResponse: String,
        expectedCount: Int,
    ): List<InterviewQuestion> {
        val jsonText = extractJson(rawResponse)
        if (jsonText.isBlank()) return emptyList()

        return runCatching {
            val root = JsonParser.parseString(jsonText)
            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject && root.asJsonObject.has("questions") -> root.asJsonObject.getAsJsonArray("questions")
                else -> null
            } ?: return emptyList()

            array.mapNotNull { element ->
                val obj = element?.asJsonObject ?: return@mapNotNull null
                val question = obj.get("question")
                    ?.asString
                    ?.cleanupQuestion()
                    .orEmpty()
                if (question.isBlank()) return@mapNotNull null

                InterviewQuestion(
                    id = UUID.randomUUID().toString(),
                    stage = stagePlan.stage,
                    difficulty = stagePlan.difficulty,
                    domain = stagePlan.domain,
                    question = question,
                    focusArea = obj.get("focusArea")?.asString?.trim()?.takeIf { it.isNotBlank() },
                )
            }
                .distinctBy { normalizeQuestion(it.question) }
                .take(expectedCount)
        }.getOrDefault(emptyList())
    }

    fun supplementStageQuestions(
        stagePlan: InterviewStagePlan,
        resume: ResumeData,
        existingQuestions: List<InterviewQuestion>,
        requiredCount: Int,
        config: InterviewConfig,
    ): List<InterviewQuestion> {
        if (requiredCount <= 0) return emptyList()

        val seen = existingQuestions.map { normalizeQuestion(it.question) }.toMutableSet()
        val focusAreas = focusAreasFor(
            resume = resume,
            stage = stagePlan.stage,
        )
        val templates = fallbackTemplates(
            stagePlan = stagePlan,
            resume = resume,
            focusAreas = focusAreas,
            config = config,
        )

        return templates
            .map { (question, focusArea) ->
                InterviewQuestion(
                    id = UUID.randomUUID().toString(),
                    stage = stagePlan.stage,
                    difficulty = stagePlan.difficulty,
                    domain = stagePlan.domain,
                    question = question,
                    focusArea = focusArea,
                )
            }
            .filter { candidate -> seen.add(normalizeQuestion(candidate.question)) }
            .take(requiredCount)
    }

    private fun stageInstruction(
        stagePlan: InterviewStagePlan,
        resume: ResumeData,
    ): String {
        return when (stagePlan.stage) {
            InterviewStage.HR ->
                "Generate HR interview questions focusing on communication, behavior, teamwork, and personality. No technical questions."
            InterviewStage.TECH1 ->
                "For a ${resume.profession} with ${resume.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for TECH1. Focus on ${resume.allSkills.joinToString().ifBlank { resume.primaryDomain }}. Include real-world scenarios if applicable. Avoid duplicates."
            InterviewStage.TECH2 ->
                "For a ${resume.profession} with ${resume.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for TECH2. Focus on ${resume.allSkills.joinToString().ifBlank { resume.primaryDomain }}. Include real-world scenarios if applicable. Avoid duplicates."
            InterviewStage.TECH3 ->
                "For a ${resume.profession} with ${resume.yearsOfExperience} years of experience, generate ${stagePlan.promptDifficultyLabel} level technical interview questions for TECH3. Focus on ${resume.allSkills.joinToString().ifBlank { resume.primaryDomain }}. Include resume-based questions, project deep dives, real-world problems, and scenario-based reasoning. Avoid duplicates."
        }
    }

    private fun experienceInstruction(
        experienceBand: ExperienceBand,
        stagePlan: InterviewStagePlan,
    ): String {
        return when (stagePlan.stage) {
            InterviewStage.HR -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER ->
                    "Candidate has up to 1 year of experience. Keep HR questions grounded in learning mindset, teamwork, ownership, and communication."
                ExperienceBand.MID_LEVEL ->
                    "Candidate has 2 to 4 years of experience. Focus HR questions on collaboration, conflict handling, prioritization, and accountability."
                ExperienceBand.SENIOR ->
                    "Candidate has 5+ years of experience. Focus HR questions on leadership, stakeholder management, mentoring, and decision-making."
            }
            InterviewStage.TECH1 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER ->
                    "Difficulty mapping for this round: EASY."
                ExperienceBand.MID_LEVEL ->
                    "Difficulty mapping for this round: BASIC."
                ExperienceBand.SENIOR ->
                    "Difficulty mapping for this round: INTERMEDIATE."
            }
            InterviewStage.TECH2 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER ->
                    "Difficulty mapping for this round: EASY to MEDIUM."
                ExperienceBand.MID_LEVEL ->
                    "Difficulty mapping for this round: INTERMEDIATE."
                ExperienceBand.SENIOR ->
                    "Difficulty mapping for this round: ADVANCED."
            }
            InterviewStage.TECH3 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER ->
                    "Difficulty mapping for this round: MEDIUM."
                ExperienceBand.MID_LEVEL ->
                    "Difficulty mapping for this round: ADVANCED."
                ExperienceBand.SENIOR ->
                    "Difficulty mapping for this round: HARD scenario and system design."
            }
        }
    }

    private fun focusAreasFor(
        resume: ResumeData,
        stage: InterviewStage,
    ): List<String> {
        if (stage == InterviewStage.HR) {
            return listOf(
                "Communication",
                "Teamwork",
                "Conflict Resolution",
                "Ownership",
                "Leadership",
                "Adaptability",
                "Feedback",
                "Career Motivation",
            )
        }

        val defaults = when (resume.primaryDomain) {
            "Android" -> listOf(
                "Kotlin",
                "Android SDK",
                "Jetpack Compose",
                "MVVM",
                "Clean Architecture",
                "Coroutines",
                "Room",
                "Performance",
                "Architecture",
            )
            "ASP.NET" -> listOf(
                "C#",
                "ASP.NET Core",
                "Web API",
                "Entity Framework",
                "Dependency Injection",
                "SQL Server",
                "Caching",
                "Architecture",
            )
            "Backend" -> listOf(
                "Java",
                "Spring Boot",
                "REST APIs",
                "Databases",
                "Caching",
                "Messaging",
                "Concurrency",
                "Scalability",
                "System Design",
            )
            "Web" -> listOf(
                "JavaScript",
                "TypeScript",
                "React",
                "State Management",
                "Rendering",
                "Performance",
                "Testing",
                "Frontend Architecture",
            )
            else -> listOf(
                "Problem Solving",
                "OOP",
                "Data Structures",
                "Algorithms",
                "Databases",
                "APIs",
                "Testing",
                "System Design",
            )
        }

        val stageBoosts = when (stage) {
            InterviewStage.HR -> emptyList()
            InterviewStage.TECH1 -> listOf("Fundamentals", "Core Concepts")
            InterviewStage.TECH2 -> listOf("Implementation", "Debugging", "Testing")
            InterviewStage.TECH3 -> listOf("Architecture", "Trade-offs", "Incidents", "Project Deep Dive", "System Design")
        }

        return (resume.allSkills + defaults + stageBoosts).distinct().take(12)
    }

    private fun fallbackTemplates(
        stagePlan: InterviewStagePlan,
        resume: ResumeData,
        focusAreas: List<String>,
        config: InterviewConfig,
    ): List<Pair<String, String>> {
        return when (stagePlan.stage) {
            InterviewStage.HR -> hrFallbackTemplates(resume)
            InterviewStage.TECH1 -> focusAreas.flatMap { focusArea ->
                listOf(
                    "What is $focusArea, and why is it important for a ${resume.profession}?",
                    "Where have you used $focusArea in a real project or learning exercise?",
                    "What problem does $focusArea solve in ${resume.primaryDomain} development?",
                    "What are the core fundamentals every engineer should know about $focusArea?",
                ).map { it to focusArea }
            }
            InterviewStage.TECH2 -> focusAreas.flatMap { focusArea ->
                listOf(
                    "How would you implement $focusArea in a production project?",
                    "What common mistakes do engineers make while working with $focusArea?",
                    "How would you debug an issue related to $focusArea?",
                    "How would you test a feature built around $focusArea?",
                    "What trade-offs would you consider while choosing an approach around $focusArea?",
                ).map { it to focusArea }
            }
            InterviewStage.TECH3 -> {
                val advancedPrompts = focusAreas.flatMap { focusArea ->
                    listOf(
                        "Describe a scenario where $focusArea becomes a scalability or maintenance problem. How would you redesign it?",
                        "What trade-offs would you evaluate before adopting $focusArea in a large production system?",
                        "How would you diagnose a complex production issue related to $focusArea?",
                        "How would you improve performance, reliability, or architecture around $focusArea?",
                    ).map { it to focusArea }
                }

                if (!config.includeResumeQuestions) {
                    advancedPrompts
                } else {
                    advancedPrompts + listOf(
                        "Looking at your resume, which project best demonstrates your depth as a ${resume.profession}, and why?" to "Resume",
                        "Tell me about a resume project where ${focusAreas.firstOrNull() ?: resume.primaryDomain} played an important role. What technical decisions did you own?" to "Resume",
                        "Which resume project forced the hardest architecture trade-off, and what did you choose?" to "Resume",
                        "Describe a production issue or performance bottleneck from your resume work. How did you resolve it?" to "Resume",
                        "If you revisited one technical project from your resume today, what would you redesign and why?" to "Resume",
                    )
                }
            }
        }
    }

    private fun hrFallbackTemplates(
        resume: ResumeData,
    ): List<Pair<String, String>> {
        val focusArea = "HR"
        return listOf(
            "Tell me about yourself and the kind of role you are looking for.",
            "What are your biggest strengths, and how have they helped you at work?",
            "Why are you interested in this opportunity?",
            "How would your teammates describe your working style?",
            "What motivates you to do your best work?",
            "What is one weakness you are actively working on?",
            "How do you handle pressure or tight deadlines?",
            "What do you value most in a manager or team culture?",
            "Tell me about a time you had a conflict with a teammate. How did you handle it?",
            "Describe a situation where you received difficult feedback. What did you do with it?",
            "How do you prioritize when several people need your help at once?",
            "Tell me about a time you had to collaborate with a difficult stakeholder.",
            "Describe a time you made a mistake at work. How did you respond?",
            "How do you build trust with a new team?",
            "Describe a time you had to lead through ambiguity or uncertainty.",
            "Tell me about a situation where you had to influence without authority.",
            "How would you handle an underperforming teammate on a critical project?",
            "Describe a high-stakes disagreement with a stakeholder and how you resolved it.",
            "Which role on your resume helped you grow the most as a teammate or leader, and why?",
            "Which project on your resume best demonstrates your communication or collaboration skills?",
            "How has your ${resume.profession} experience changed the way you work with product, design, or other stakeholders?",
            "What kind of environment helps you do your best work as a ${resume.profession}?",
        ).map { it to focusArea }
    }

    private fun difficultyProfileFor(
        stage: InterviewStage,
        experienceBand: ExperienceBand,
    ): DifficultyProfile {
        return when (stage) {
            InterviewStage.HR -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER -> DifficultyProfile(
                    difficulty = QuestionDifficulty.EASY,
                    promptLabel = "behavioral foundation",
                )
                ExperienceBand.MID_LEVEL -> DifficultyProfile(
                    difficulty = QuestionDifficulty.MEDIUM,
                    promptLabel = "behavioral and collaboration",
                )
                ExperienceBand.SENIOR -> DifficultyProfile(
                    difficulty = QuestionDifficulty.HARD,
                    promptLabel = "leadership and stakeholder",
                )
            }
            InterviewStage.TECH1 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER -> DifficultyProfile(
                    difficulty = QuestionDifficulty.EASY,
                    promptLabel = "easy",
                )
                ExperienceBand.MID_LEVEL -> DifficultyProfile(
                    difficulty = QuestionDifficulty.EASY,
                    promptLabel = "basic",
                )
                ExperienceBand.SENIOR -> DifficultyProfile(
                    difficulty = QuestionDifficulty.MEDIUM,
                    promptLabel = "intermediate",
                )
            }
            InterviewStage.TECH2 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER -> DifficultyProfile(
                    difficulty = QuestionDifficulty.MEDIUM,
                    promptLabel = "easy-medium",
                )
                ExperienceBand.MID_LEVEL -> DifficultyProfile(
                    difficulty = QuestionDifficulty.MEDIUM,
                    promptLabel = "intermediate",
                )
                ExperienceBand.SENIOR -> DifficultyProfile(
                    difficulty = QuestionDifficulty.HARD,
                    promptLabel = "advanced",
                )
            }
            InterviewStage.TECH3 -> when (experienceBand) {
                ExperienceBand.EARLY_CAREER -> DifficultyProfile(
                    difficulty = QuestionDifficulty.MEDIUM,
                    promptLabel = "medium",
                )
                ExperienceBand.MID_LEVEL -> DifficultyProfile(
                    difficulty = QuestionDifficulty.HARD,
                    promptLabel = "advanced",
                )
                ExperienceBand.SENIOR -> DifficultyProfile(
                    difficulty = QuestionDifficulty.HARD,
                    promptLabel = "hard scenario and system design",
                )
            }
        }
    }

    private fun domainForStage(
        stage: InterviewStage,
        resume: ResumeData,
    ): String {
        return when (stage) {
            InterviewStage.HR -> "HR"
            InterviewStage.TECH1,
            InterviewStage.TECH2,
            InterviewStage.TECH3,
            -> resume.primaryDomain.ifBlank { "General" }
        }
    }

    private fun linkedStageMap(
        hrCount: Int,
        tech1Count: Int,
        tech2Count: Int,
        tech3Count: Int,
    ): LinkedHashMap<InterviewStage, Int> {
        return linkedMapOf(
            InterviewStage.HR to hrCount,
            InterviewStage.TECH1 to tech1Count,
            InterviewStage.TECH2 to tech2Count,
            InterviewStage.TECH3 to tech3Count,
        )
    }

    private fun extractJson(rawResponse: String): String {
        val trimmed = rawResponse.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed.removeCodeFenceMarkers()
        }

        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""")
            .find(rawResponse)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!fenced.isNullOrBlank()) return fenced

        val objectStart = rawResponse.indexOf('{')
        val objectEnd = rawResponse.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return rawResponse.substring(objectStart, objectEnd + 1).trim()
        }

        val arrayStart = rawResponse.indexOf('[')
        val arrayEnd = rawResponse.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return rawResponse.substring(arrayStart, arrayEnd + 1).trim()
        }

        return ""
    }

    private fun String.removeCodeFenceMarkers(): String {
        return removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun String.cleanupQuestion(): String {
        return replace(Regex("""^\d+[\).\-\s]+"""), "")
            .replace("*", "")
            .replace("\"", "")
            .trim()
    }

    private fun normalizeQuestion(question: String): String {
        return question
            .lowercase()
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

data class StageGenerationRequest(
    val stage: InterviewStage,
    val count: Int,
    val difficulty: QuestionDifficulty,
    val domain: String,
    val prompt: String,
)

private data class DifficultyProfile(
    val difficulty: QuestionDifficulty,
    val promptLabel: String,
)
