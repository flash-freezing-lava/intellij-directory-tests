package me.ffl.intellijDirectoryTests

import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.readText

val documentationExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "project"
    val projectFiles = beforeDir.loadProject()
    projectFiles.findCarets().forEach { caret ->
        val doc = caret.file.getDocumentationAt(caret.offset)
        if (doc == null) {
            if (caret.name == null) fail("no documentation found at nameless caret")
            else fail("no documentation found at caret ${caret.name}")
        } else {
            val expected = (testDataPath / "result.html").readText()
            doc.trimEnd() shouldBe expected.trimEnd()
        }
    }
}