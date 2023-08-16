package me.ffl.intellijDirectoryTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import io.kotest.assertions.*
import io.kotest.assertions.print.Printed
import io.kotest.assertions.print.printed
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldHave
import java.nio.file.Path
import kotlin.io.path.*

class KotestExecutorContext(
    val testName: String,
    val testDataPath: Path,
    val myFixture: CodeInsightTestFixture,
    val config: DirectoryTestConfig,
) {
    fun loadBeforeProject(): List<MarkupFile> = (testDataPath / "before").loadProject()

    /**
     * Compare if the resulting code and caret position matches the specified result in the `after` directory.
     */
    fun checkAfterProject() {
        val openFileName = myFixture.file.name
        val caret = myFixture.caretOffset
        val afterRootPath = testDataPath / "after"
        val currentRoot = myFixture.tempDirFixture.getFile(".")!!

        fun checkFile(file: VirtualFile) {
            val currentFiles = file.children
            val relPath = Path(currentRoot.path).relativize(Path(file.path))
            val referencePath = afterRootPath / relPath

            if (file.isDirectory) {
                if (!referencePath.isDirectory()) {
                    fail("$relPath was a directory, but should have been a regular file")
                    return
                }
                val referenceFiles = referencePath.listDirectoryEntries()
                currentFiles.forEach { currentFile ->
                    checkFile(currentFile)
                }
                val missingFiles = referenceFiles.filter { afterFile -> currentFiles.none { it.name == afterFile.name } }
                if (missingFiles.isNotEmpty()) {
                    fail("Missed files ${missingFiles.map(afterRootPath::relativize)}")
                }
            } else {
                if (!referencePath.exists()) {
                    fail("$relPath has no corresponding file in after project")
                } else if (!referencePath.isFile()) {
                    fail("$relPath is not a file in after project")
                } else {
                    myFixture.openFileInEditor(file)
                    val afterFileMarkup = MarkupFile(myFixture, null, referencePath.readText(), file.name)
                    val expected = afterFileMarkup.code
                    val actual = myFixture.editor.document.text
                    if (expected != actual) {
                        errorCollector.collectOrThrow(failure(Expected(Printed(expected)), Actual(Printed(actual)), "Unexpected content in file ${file.name}:\n"))
                    }

                    val requiredCarets = afterFileMarkup.findCarets()
                    // Use assert instead of kotest, because this is not an error in the tested plugin but in the test itself
                    check(requiredCarets.any { it.name != null } || requiredCarets.size <= 1) { "Multiple carets in result projects are not supported. Only use one caret without a name." }
                    val requiredCaret = requiredCarets.firstOrNull() ?: return
                    withClue("Caret in unexpected file") {
                        openFileName shouldBe file.name
                    }
                    if (requiredCaret.offset != caret) {
                        failure(Expected(Printed(afterFileMarkup.lineCol(caret).toString())), Actual(Printed(afterFileMarkup.lineCol(requiredCaret.offset).toString())), "Caret in unexpected position")
                    }
                }
            }
        }

        checkFile(currentRoot)
    }

    fun Path.loadProject(): List<MarkupFile> = runWriteAction {
        val referenceRootPath = this
        val root = myFixture.tempDirFixture.getFile(".")!!

        fun markupFile(path: Path): List<MarkupFile> {
            val relPath = referenceRootPath.relativize(path)
            return if (path.isDirectory()) {
                // Don't create project dir. It already exists.
                if (referenceRootPath != path) VfsTestUtil.createDir(root, relPath.toString())
                path.listDirectoryEntries().flatMap(::markupFile)
            } else {
                val file = VfsTestUtil.createFile(root, relPath.toString())
                listOf(MarkupFile(myFixture, file, path.readText()).apply { assertExactParsingErrors() })
            }
        }

        return@runWriteAction markupFile(referenceRootPath)
    }

    fun fail(message: String, cause: Throwable? = null) {
        errorCollector.collectOrThrow(failure(message, cause))
    }
}