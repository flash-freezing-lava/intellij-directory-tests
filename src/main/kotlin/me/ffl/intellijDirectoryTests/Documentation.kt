package me.ffl.intellijDirectoryTests

import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div
import kotlin.io.path.readText

val documentationExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "project"
    val projectFiles = beforeDir.loadProject()
    val caret = projectFiles.findCaret()
    val doc = caret.file.getDocumentationAt(caret.offset)
    if (doc == null) {
        fail("no documentation found at caret")
    } else {
        val expected = (testDataPath / "result.html").readText()
        doc.trimEnd() shouldBe expected.trimEnd()
    }
}