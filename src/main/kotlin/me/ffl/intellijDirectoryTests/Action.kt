package me.ffl.intellijDirectoryTests

import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.DirectoryTestConfig.Companion.simplifyIntentionName
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

val actionExecutor: KotestExecutor = {
    val beforeDir = testDataPath / "before"
    val beforeFiles = beforeDir.loadProject()
    val errorFile = testDataPath / "should_fail.txt"
    val (caretFile, caretOffset) = beforeFiles.findCaret()
    val intention = testDataPath.mapNotNull { config.knownIntentionMap[it.toString().simplifyIntentionName()] }.lastOrNull().shouldNotBeNull {
        "test data path contains no Intention class name"
    }
    try {
        caretFile.executeIntentionAt(intention, caretOffset)
        if (errorFile.exists()) {
            fail("Intention did not fail, when it was expected")
        }
    } catch (e: RefactoringErrorHintException) {
        if (!errorFile.exists()) {
            fail("unexpected intention error: ${e.message}")
        } else {
            val expected = errorFile.readText()
            e.message shouldBe expected
        }
    }
    checkAfterProject()
}