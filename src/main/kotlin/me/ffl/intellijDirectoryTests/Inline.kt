package me.ffl.intellijDirectoryTests

import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div

val inlineExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "before"
    val beforeFiles = beforeDir.loadProject()
    val (caretFile, caretOffset) = checkNotNull(beforeFiles.findCarets().singleOrNull()) {
        "Inline tests must only have one caret"
    }
    caretFile.inlineAt(caretOffset)
    checkAfterProject()
}