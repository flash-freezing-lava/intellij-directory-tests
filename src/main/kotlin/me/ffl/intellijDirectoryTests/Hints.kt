package me.ffl.intellijDirectoryTests

import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div
import kotlin.io.path.readText

val hintsExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "project"
    val projectFiles = beforeDir.loadProject()
    val caret = projectFiles.findCaret()
    val expected = (testDataPath / "result.txt").readText()
    val hintOrNull = caret.file.getHintAt(caret.offset)
    if (expected.isEmpty()) {
        hintOrNull shouldBe null
    } else {
        if (hintOrNull == null) {
            fail("no hint found at caret")
        } else {
            hintOrNull.trimEnd() shouldBe expected.trimEnd()
        }
    }
}