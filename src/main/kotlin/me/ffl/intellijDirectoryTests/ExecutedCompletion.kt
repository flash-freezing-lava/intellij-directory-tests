package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.refactoring.suggested.startOffset
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContainDuplicates
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

private fun KotestExecutorContext.selectPsi(caret: FoundCaret, projectFiles: List<MarkupFile>, psiLookUps: List<LookupElement>): LookupElement? {
    val projectVirtualFiles = projectFiles.map { it.vFile }
    val (internalReferences, externalReferences) = psiLookUps.partition { it.psiElement!!.containingFile.virtualFile in projectVirtualFiles }
    val internal = selectInternal(caret, projectFiles, internalReferences)
    val externalReferenceFileName = "external suggestions.txt"
    val externalReferenceFile = testDataPath / externalReferenceFileName
    require(!externalReferenceFile.exists() || internal == null) {
        "Both an internal and an external reference was specified, but completion tests must select a single element to completed"
    }
    if (internal != null) return internal
    if (!externalReferenceFile.exists()) return null
    val externalLines = externalReferenceFile.readLines().shouldNotContainDuplicates().filter { it.isNotEmpty() }.toSet()
    return selectExternal(externalReferences, externalLines)
}

private fun selectInternal(caret: FoundCaret, projectFiles: List<MarkupFile>, internalLookupItems: List<LookupElement>): LookupElement? {
    val matches = projectFiles.associateWith { it.findWantedReferencePositions(caret) }.filter { it.value.isNotEmpty() }
    val singleMatches = matches.mapValues { (file, pos) ->
        require(pos.size <= 1) {
            "File: ${file.name}: Multiple lookup items were marked, but completion tests must select a single element to completed"
        }
        pos.single()
    }
    require(singleMatches.size <= 1) {
        "Multiple lookup items were marked (one in files ${singleMatches.keys.map { it.name }}), but completion tests must select a single element to completed"
    }
    val (file, pos) = singleMatches.entries.firstOrNull() ?: return null
    val matchingLookupItems = internalLookupItems.filter {
        val psiElement = it.psiElement!!
        psiElement.containingFile.virtualFile == file.vFile && psiElement.startOffset == pos
    }
    require(matchingLookupItems.isNotEmpty()) {
        "The element at ${file.name}:${file.lineCol(pos)} was not among the completion entries"
    }
    require(matchingLookupItems.size <= 1) {
        "Multiple lookup items matched the marker in file ${file.name}, but completion tests must select a single element to completed"
    }
    return matchingLookupItems.firstOrNull()
}

private fun KotestExecutorContext.selectExternal(externalReferences: List<LookupElement>, externalLines: Set<String>): LookupElement {
    require(externalLines.size <= 1) {
        "Multiple lookup items were specified as external references, but completion tests must select a single element to completed"
    }
    require(externalLines.size == 1) {
        "No lookup items was specified as external references, but completion tests must select a single element to completed"
    }
    val wantedName = externalLines.single()
    val foundExternalReferences = externalReferences.filter {
        val psiElement = it.psiElement!!
        val name: String? = config.externalReferenceToString(psiElement)
        if (name == null) {
            val virtualFile = psiElement.containingFile.virtualFile
            myFixture.openFileInEditor(virtualFile)
            val pos = myFixture.editor.offsetToLogicalPosition(psiElement.startOffset)
            val errorElementName = "Unknown element of type ${it.javaClass.simpleName} in file ${virtualFile.presentableName} at position ${pos.line}:${pos.column}"
            fail("Unexpected suggestion: $errorElementName")
            false
        } else {
            name == wantedName
        }
    }
    withClue("The element at '${wantedName}' was not among the completion entries") {
        foundExternalReferences.shouldNotBeEmpty()
    }
    require(foundExternalReferences.size == 1) {
        "Multiple lookup items with external name '$wantedName', but completion tests must select a single element to completed"
    }
    return foundExternalReferences.single()
}

private fun selectNonPsi(nonPsiLookups: List<LookupElement>, nonPsiLines: Set<String>): LookupElement {
    require(nonPsiLines.size <= 1) {
        "Multiple lookup items were specified as non-psi items, but completion tests must select a single element to completed"
    }
    require(nonPsiLines.size == 1) {
        "No lookup items was specified as non-psi item, but completion tests must select a single element to completed"
    }
    val wantedName = nonPsiLines.single()
    val foundNonPsiReferences = nonPsiLookups.filter {
        it.lookupString == wantedName
    }
    withClue("The element at '${wantedName}' was not among the completion entries") {
        foundNonPsiReferences.shouldNotBeEmpty()
    }
    require(foundNonPsiReferences.size == 1) {
        "Multiple non-psi lookup items with name '$wantedName', but completion tests must select a single element to completed"
    }
    return foundNonPsiReferences.single()
}

private fun KotestExecutorContext.select(caret: FoundCaret, beforeFiles: List<MarkupFile>, lookupElements: Array<out LookupElement>): LookupElement {
    val (psiReferences, nonPsiReferences) = lookupElements.partition { it.psiElement != null }
    val psi = selectPsi(caret, beforeFiles, psiReferences)
    val nonPsiFileName = "non_psi_items.txt"
    val nonPsiFilePath = testDataPath / nonPsiFileName
    require(!nonPsiFilePath.exists() || psi == null) {
        "Both a psi reference and a non-psi lookup item were specified, but completion tests must select a single element to completed"
    }
    if (psi != null) return psi
    require(nonPsiFilePath.exists()) {
        "No internal, external or non-psi lookup element was specified"
    }
    val nonPsiLines = nonPsiFilePath.readLines().shouldNotContainDuplicates().filter { it.isNotEmpty() }.toSet()
    return selectNonPsi(nonPsiReferences, nonPsiLines)
}

val executedCompletionExecutor: KotestExecutor = {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false
    val beforeFiles = loadBeforeProject()
    val caret = checkNotNull(beforeFiles.findCarets().singleOrNull()) {
        "Executing completion tests must only have one caret"
    }
    val results = checkNotNull(caret.file.executeCompletionAt(caret.offset)) {
        "Test code failed to disable autocompletion"
    }

    myFixture.openFileInEditor(caret.file.vFile)
    myFixture.editor.caretModel.moveToOffset(caret.offset)

    val selectedElement = select(caret, beforeFiles, results)

    val lookupImpl = LookupManager.getActiveLookup(myFixture.editor) as LookupImpl
    runWriteAction {
        lookupImpl.finishLookup(Lookup.NORMAL_SELECT_CHAR, selectedElement)
    }

    checkAfterProject()

    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true
}