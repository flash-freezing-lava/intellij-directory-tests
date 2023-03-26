package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.startOffset
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContainDuplicates
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

private fun KotestExecutorContext.checkNonPsi(nonPsiLookups: Set<String>) {
    val nonPsiFilePath = testDataPath / "non_psi_items.txt"
    val expectedNonPsi = if (nonPsiFilePath.exists()) nonPsiFilePath.readLines().toSet() else emptySet()
    val missingNonPsi = expectedNonPsi - nonPsiLookups
    withClue("Not all non-psi lookups were suggested by the auto-completion") {
        missingNonPsi.shouldBeEmpty()
    }
    val additionalNonPsi = nonPsiLookups - expectedNonPsi
    withClue("Unexpected non-psi lookup items were suggested by the auto-completion") {
        additionalNonPsi.shouldBeEmpty()
    }
}

private fun KotestExecutorContext.checkPsi(projectFiles: List<MarkupFile>, psiLookUps: List<PsiElement>) {
    val projectVirtualFiles = projectFiles.map { it.vFile }
    val (internalReferences, externalReferences) = psiLookUps.partition { it.containingFile.virtualFile in projectVirtualFiles }
    checkInternalPsi(projectFiles, internalReferences)
    checkExternalPsi(externalReferences)
}

private fun KotestExecutorContext.checkInternalPsi(projectFiles: List<MarkupFile>, internalReferences: List<PsiElement>) {
    projectFiles.forEach { markupFile ->
        val wantedReferences = markupFile.findWantedReferencePositions()
        val foundReferences = internalReferences.filter {
            it.containingFile.virtualFile == markupFile.vFile
        }.map { referencedElement ->
            val start = (referencedElement as? PsiNameIdentifierOwner)?.nameIdentifier ?: referencedElement
            if (start.startOffset !in wantedReferences) {
                fail(
                    """file ${markupFile.name}: ${referencedElement.javaClass.simpleName} at ${markupFile.lineCol(start.startOffset)}  was not supposed to be suggested"""
                )
            }
            start.startOffset
        }
        val foundTimes = foundReferences.groupBy { it }.mapValues { it.value.size }
        foundTimes.forEach { (pos, times) ->
            if (times != 1) {
                fail(
                    "file ${markupFile.name}: element at position ${markupFile.lineCol(pos)} was suggested $times times"
                )
            }
        }
        val missedReferences = wantedReferences.filter { it !in foundReferences }.map(markupFile::lineCol)
        if (missedReferences.isNotEmpty()) {
            fail(
                "file ${markupFile.name}: elements at positions $missedReferences were not suggested"
            )
        }
    }
}

private fun KotestExecutorContext.checkExternalPsi(externalReferences: List<PsiElement>) {
    val externalReferenceFile = testDataPath / "external suggestions.txt"
    val wantedExternalReferences = if (externalReferenceFile.exists()) externalReferenceFile.readLines().shouldNotContainDuplicates().toSet() else emptySet()
    val foundExternalReferences = externalReferences.map {
        var name: String? = config.externalReferenceToString(it)
        if (name == null) {
            val virtualFile = it.containingFile.virtualFile
            myFixture.openFileInEditor(virtualFile)
            val pos = myFixture.editor.offsetToLogicalPosition(it.startOffset)
            name = "unknown element of type ${it.javaClass.simpleName} in file ${virtualFile.presentableName} at position ${pos.line}:${pos.column}"
            fail("unexpected suggestion: $name")
        } else {
            if (name !in wantedExternalReferences) {
                fail("unexpected suggestion: $name")
            }
        }
        name
    }.toSet()
    val missingExternalReferences = wantedExternalReferences - foundExternalReferences
    if (missingExternalReferences.isNotEmpty()) {
        fail("didn't suggest external language elements $missingExternalReferences")
    }
}

val completionExecutor: KotestExecutor = {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false
    val projectFiles = testDataPath.loadProject()
    val caret = projectFiles.findCaret()
    val results = checkNotNull(caret.file.executeCompletionAt(caret.offset)) {
        "Test code failed to disable autocompletion"
    }

    val nonPsiLookups = results.filter { it.psiElement == null }.map { it.lookupString }.toSet()
    checkNonPsi(nonPsiLookups)
    val psiLookUps = results.mapNotNull { it.psiElement }
    checkPsi(projectFiles, psiLookUps)

    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true
}