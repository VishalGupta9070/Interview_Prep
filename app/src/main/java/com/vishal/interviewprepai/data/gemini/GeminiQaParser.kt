package com.vishal.interviewprepai.data.gemini

/**
 * Gemini returns plain text. This parser extracts (question, answer) pairs
 * from common formats like:
 * - Q: ... / A: ...
 * - "Question:" / "Answer:"
 * - Numbered questions with following answer lines.
 */
object GeminiQaParser {
    private val questionPrefix = Regex("""^(?:\*{0,2})\s*(?:q(?:uestion)?\s*\d*|(?:\d+[\).\-\s]))\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val answerPrefix = Regex("""^(?:\*{0,2})\s*(?:a(?:nswer)?\s*\d*)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val numberedQuestion = Regex("""^(?:\*{0,2})\s*(\d+)[\).\-\s]+(.+)$""")

    fun parse(text: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()

        val lines = text.lines().map { it.trim() }
        val out = mutableListOf<Pair<String, String>>()

        var currentQuestion: String? = null
        val currentAnswer = StringBuilder()

        fun flush() {
            val q = currentQuestion?.trim().orEmpty()
            val a = currentAnswer.toString().trim()
            if (q.isNotBlank() && a.isNotBlank()) out += q to a
            currentQuestion = null
            currentAnswer.clear()
        }

        for (line in lines) {
            if (line.isBlank()) continue
            val qMatch = questionPrefix.find(line)
            val aMatch = answerPrefix.find(line)
            val nMatch = numberedQuestion.find(line)

            when {
                qMatch != null -> {
                    flush()
                    currentQuestion = qMatch.groupValues[1].trim()
                }
                // Numbered question fallback (e.g., "1. Explain ...")
                nMatch != null && !line.startsWith("A", ignoreCase = true) -> {
                    flush()
                    currentQuestion = nMatch.groupValues[2].trim()
                }
                aMatch != null -> {
                    val cleaned = aMatch.groupValues[1].trim()
                    if (cleaned.isNotBlank()) currentAnswer.appendLine(cleaned)
                }
                currentQuestion != null -> currentAnswer.appendLine(line)
            }
        }
        flush()
        return out
    }
}
