package me.ffl.intellijDirectoryTests

import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.readText

val hintsExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "project"
    val projectFiles = beforeDir.loadProject()
    projectFiles.findCarets().forEach { caret ->
        val expected = (testDataPath / "result.txt").readText()
        val hintOrNull = caret.file.getHintAt(caret.offset)
        if (expected.isEmpty()) {
            hintOrNull shouldBe null
        } else {
            if (hintOrNull == null) {
                if (caret.name == null) fail("No hint found at nameless caret")
                else fail("No hint found at caret ${caret.name}")
            } else {
                hintOrNull.trimEnd() shouldBe expected.trimEnd()
            }
        }
    }
}