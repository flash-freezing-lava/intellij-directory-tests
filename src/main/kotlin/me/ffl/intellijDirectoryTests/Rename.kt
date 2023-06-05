package me.ffl.intellijDirectoryTests

import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import io.kotest.matchers.shouldBe
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

val renameExecutor: KotestExecutor = {
    val beforeFiles = loadBeforeProject()
    val newName = (testDataPath / "new_name.txt").readText()
    val errorFile = testDataPath / "should_fail.txt"
    val caret = checkNotNull(beforeFiles.findCarets().singleOrNull()) {
        "Rename tests must only have one caret"
    }

    try {
        caret.file.renameElementAt(caret.offset, newName)
        if (errorFile.exists()) {
            fail("Rename did not fail, when it was expected")
        }
        checkAfterProject()
    } catch (e: RefactoringErrorHintException) {
        if (!errorFile.exists()) {
            fail("Unexpected rename error: ${e.message}")
        } else {
            val expected = errorFile.readText()
            e.message shouldBe expected
        }
    }
}
