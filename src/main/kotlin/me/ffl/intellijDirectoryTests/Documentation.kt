package me.ffl.intellijDirectoryTests

import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

val documentationExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "project"
    val projectFiles = beforeDir.loadProject()
    projectFiles.findCarets().forEach { caret ->
        val doc = caret.file.getDocumentationAt(caret.offset)?.trimEnd()
        if (doc == null) {
            if (caret.name == null) fail("No documentation found at nameless caret")
            else fail("No documentation found at caret ${caret.name}")
        } else {
            val resultFile = testDataPath / "result.html"
            val expected = resultFile.readText().trimEnd()
            if (doc != expected && config.overrideDocumentationOutput) {
                resultFile.writeText(doc)
            }
            doc shouldBe expected
        }
    }
}