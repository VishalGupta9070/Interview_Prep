package com.vishal.interviewprepai.data.interview

import com.vishal.interviewprepai.domain.model.interview.ResumeData

class ResumeAnalyzer {

    private val profiles = listOf(
        DomainProfile(
            name = "Android",
            professionFallback = "Android Developer",
            keywords = listOf(
                "android",
                "kotlin",
                "jetpack",
                "compose",
                "room",
                "coroutines",
                "stateflow",
                "mvvm",
            ),
            skills = linkedMapOf(
                "Kotlin" to listOf("kotlin"),
                "Android SDK" to listOf("android", "android sdk"),
                "Jetpack Compose" to listOf("jetpack compose", "compose"),
                "MVVM" to listOf("mvvm"),
                "Clean Architecture" to listOf("clean architecture"),
                "Coroutines" to listOf("coroutines"),
                "StateFlow" to listOf("stateflow"),
                "Room" to listOf("room"),
                "Retrofit" to listOf("retrofit"),
            ),
        ),
        DomainProfile(
            name = "ASP.NET",
            professionFallback = "ASP.NET Developer",
            keywords = listOf(
                "asp.net",
                ".net",
                "dotnet",
                "c#",
                "web api",
                "entity framework",
                "linq",
            ),
            skills = linkedMapOf(
                "C#" to listOf("c#"),
                "ASP.NET Core" to listOf("asp.net core", "asp.net"),
                "Web API" to listOf("web api", "api"),
                "Entity Framework" to listOf("entity framework", "ef core", "ef"),
                "LINQ" to listOf("linq"),
                "SQL Server" to listOf("sql server"),
                "Dependency Injection" to listOf("dependency injection", "di"),
            ),
        ),
        DomainProfile(
            name = "Backend",
            professionFallback = "Backend Engineer",
            keywords = listOf(
                "backend",
                "java",
                "spring",
                "microservice",
                "rest api",
                "database",
                "sql",
                "redis",
                "kafka",
            ),
            skills = linkedMapOf(
                "Java" to listOf("java"),
                "Spring Boot" to listOf("spring boot", "spring"),
                "REST APIs" to listOf("rest api", "api"),
                "Microservices" to listOf("microservice", "microservices"),
                "SQL" to listOf("sql", "mysql", "postgresql", "postgres"),
                "Redis" to listOf("redis"),
                "Kafka" to listOf("kafka"),
                "Docker" to listOf("docker"),
            ),
        ),
        DomainProfile(
            name = "Web",
            professionFallback = "Frontend Developer",
            keywords = listOf(
                "react",
                "javascript",
                "typescript",
                "frontend",
                "html",
                "css",
                "next.js",
                "redux",
            ),
            skills = linkedMapOf(
                "JavaScript" to listOf("javascript"),
                "TypeScript" to listOf("typescript"),
                "React" to listOf("react"),
                "Next.js" to listOf("next.js", "nextjs"),
                "HTML" to listOf("html"),
                "CSS" to listOf("css"),
                "Redux" to listOf("redux"),
                "REST APIs" to listOf("api", "rest"),
            ),
        ),
        DomainProfile(
            name = "Software",
            professionFallback = "Software Developer",
            keywords = listOf(
                "software",
                "developer",
                "engineer",
                "oop",
                "data structure",
                "algorithm",
                "api",
                "database",
            ),
            skills = linkedMapOf(
                "Problem Solving" to listOf("problem solving"),
                "OOP" to listOf("oop", "object oriented"),
                "Data Structures" to listOf("data structure", "data structures"),
                "Algorithms" to listOf("algorithm", "algorithms"),
                "Databases" to listOf("database", "sql"),
                "APIs" to listOf("api", "rest"),
                "Testing" to listOf("testing", "unit test"),
                "System Design" to listOf("system design"),
            ),
        ),
    )

    fun analyze(
        resumeText: String,
        professionHint: String? = null,
    ): ResumeData {
        val cleanResume = resumeText.trim()
        val cleanHint = professionHint?.trim().orEmpty()
        val combinedText = listOfNotNull(
            cleanHint.takeIf { it.isNotBlank() },
            cleanResume,
        ).joinToString("\n")
        val normalized = combinedText.lowercase()

        val scoredProfiles = profiles.associateWith { profile ->
            profile.score(normalized) + profile.hintBoost(cleanHint)
        }
        val primaryProfile = scoredProfiles
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.key
            ?: profiles.last()

        val primarySkills = primaryProfile.detectSkills(normalized)
            .ifEmpty { primaryProfile.skills.keys.take(4) }
            .take(6)

        val secondarySkills = profiles
            .asSequence()
            .filter { it != primaryProfile }
            .flatMap { it.detectSkills(normalized).asSequence() }
            .filterNot { it in primarySkills }
            .distinct()
            .take(6)
            .toList()

        return ResumeData(
            primaryDomain = primaryProfile.name,
            profession = cleanProfession(cleanHint).ifBlank { primaryProfile.professionFallback },
            primarySkills = primarySkills,
            secondarySkills = secondarySkills,
            yearsOfExperience = extractYearsOfExperience(normalized),
            resumeText = cleanResume,
        )
    }

    private fun cleanProfession(raw: String): String {
        return raw
            .replace("*", "")
            .replace("\"", "")
            .removePrefix("-")
            .trim()
            .take(80)
    }

    private fun extractYearsOfExperience(text: String): Int {
        val directYears = Regex("""(\d{1,2}(?:\.\d+)?)\s*\+?\s*(years?|yrs?)""")
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull()?.toInt() }
            .maxOrNull()
        if (directYears != null) return directYears

        val experienced = Regex("""experience\s*[:\-]?\s*(\d{1,2})""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return experienced ?: 1
    }

    private data class DomainProfile(
        val name: String,
        val professionFallback: String,
        val keywords: List<String>,
        val skills: LinkedHashMap<String, List<String>>,
    ) {
        fun score(text: String): Int = keywords.sumOf { keyword -> keywordCount(text, keyword) }

        fun hintBoost(professionHint: String): Int {
            if (professionHint.isBlank()) return 0
            val normalizedHint = professionHint.lowercase()
            val base = if (normalizedHint.contains(name.lowercase())) 4 else 0
            return base + keywords.take(3).sumOf { keyword -> keywordCount(normalizedHint, keyword) }
        }

        fun detectSkills(text: String): List<String> {
            return skills
                .filterValues { aliases -> aliases.any { alias -> containsKeyword(text, alias) } }
                .keys
                .toList()
        }

        private fun keywordCount(text: String, keyword: String): Int {
            return Regex("""(?<![a-z0-9])${Regex.escape(keyword.lowercase())}(?![a-z0-9])""")
                .findAll(text)
                .count()
        }

        private fun containsKeyword(text: String, keyword: String): Boolean = keywordCount(text, keyword) > 0
    }
}
