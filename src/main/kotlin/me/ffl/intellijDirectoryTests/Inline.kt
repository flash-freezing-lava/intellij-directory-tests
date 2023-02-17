package me.ffl.intellijDirectoryTests

import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div

val inlineExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "before"
    val beforeFiles = beforeDir.loadProject()
    val (caretFile, caretOffset) = beforeFiles.findCaret()
    caretFile.inlineAt(caretOffset)
    checkAfterProject()
}