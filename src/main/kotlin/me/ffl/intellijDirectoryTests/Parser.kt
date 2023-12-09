package me.ffl.intellijDirectoryTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.ParsingTestUtil
import io.kotest.matchers.shouldBe
import kotlin.io.path.*

val parserExecutor: KotestExecutor = {
    val inputFile = testDataPath.listDirectoryEntries().singleOrNull {
        (it.nameWithoutExtension == testName || it.nameWithoutExtension == "input") && it.extension != "txt"
    } ?: error("No or multiple input files found")
    val txtFile = testDataPath / "$testName.txt"
    val inputMarkupFile = runWriteAction { MarkupFile(myFixture, myFixture.createFile("${inputFile.fileName}", ""), inputFile.readText()) }
    val inputPsiFile = myFixture.psiManager.findFile(inputMarkupFile.vFile) ?: error("Markup file without psi file")
    val inputFileExtension = inputFile.extension
    assert(!(testDataPath / "allow_errors.txt").exists()) { "Migrate outdated allow_errors.txt to newer <parse-error> format" }
    inputMarkupFile.assertExactParsingErrors()
    if (!inputMarkupFile.expectsParseErrors) {
        ParsingTestUtil.assertNoPsiErrorElements(inputPsiFile)
    }
    var expected = txtFile.readText()
    // rewrite files, that were created with the in-memory PSI viewer
    if (expected.contains("File(Dummy.$inputFileExtension)")) {
        val new = expected.replace("File(Dummy.$inputFileExtension)", "File(${inputFile.fileName})")
        txtFile.writeText(new)
        expected = new
    }
    val actual = DebugUtil.psiToString(inputPsiFile, true, true)
    if (config.overrideParserOutput) {
        txtFile.writeText(actual.trim())
    }
    actual.trim() shouldBe expected.trim()
}