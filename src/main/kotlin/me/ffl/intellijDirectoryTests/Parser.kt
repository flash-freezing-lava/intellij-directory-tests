package me.ffl.intellijDirectoryTests

import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.ParsingTestUtil
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import io.kotest.matchers.shouldBe
import kotlin.io.path.*

val parserExecutor: KotestExecutor = {
    val inputFile = testDataPath.listDirectoryEntries().singleOrNull {
        (it.nameWithoutExtension == testName || it.nameWithoutExtension == "input") && it.extension != "txt"
    } ?: error("no or multiple input files found")
    val txtFile = testDataPath / "$testName.txt"
    val inputMarkupFile = MarkupFile(myFixture, myFixture.createFile("${inputFile.fileName}", ""), inputFile.readText())
    val inputPsiFile = myFixture.psiManager.findFile(inputMarkupFile.vFile) ?: error("markup file without psi file")
    val inputFileExtension = inputFile.extension
    assert(!(testDataPath / "allow_errors.txt").exists()) { "migrate outdated allow_errors.txt to newer <parse-error> format" }
    inputMarkupFile.assertExactParsingErrors()
    if (!inputMarkupFile.expectsParseErrors) {
        ParsingTestUtil.ensureNoErrorElements(inputPsiFile)
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