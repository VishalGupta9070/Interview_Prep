package com.vishal.interviewprepai.data.resume

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {
    fun extractText(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                return stripper.getText(doc).orEmpty()
            }
        }
    }
}

