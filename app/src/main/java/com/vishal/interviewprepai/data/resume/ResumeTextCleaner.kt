package com.vishal.interviewprepai.data.resume

object ResumeTextCleaner {
    fun clean(raw: String): String {
        return raw
            .replace("\u0000", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

