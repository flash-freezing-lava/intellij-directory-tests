package me.ffl.intellijDirectoryTests

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.startOffset
import io.kotest.matchers.collections.shouldNotContainDuplicates
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

val resolveExecutor: KotestExecutor = {
    val projectFiles = testDataPath.loadProject()
    val caret = projectFiles.findCaret()
    val reference = caret.file.findReferenceAt(caret.offset)
    val referencedElements = reference.multiResolve(false).map {
        it.element.shouldNotBeNull { "A ResolveResult had no element" }
    }
    val projectVirtualFiles = projectFiles.map { it.vFile }
    val (internalReferences, externalReferences) = referencedElements.partition { it.containingFile.virtualFile in projectVirtualFiles }
    projectFiles.forEach { markupFile ->
        val wantedReferences = markupFile.findWantedReferencePositions()
        val foundReferences = internalReferences.filter {
            it.containingFile.virtualFile == markupFile.vFile
        }.map { referencedElement ->
            if (!reference.isReferenceTo(referencedElement)) {
                fail(
                    "file ${markupFile.name}: inconsistent isReferenceTo implementation in class ${reference.javaClass} referring to \"${referencedElement.text}\" of type ${referencedElement.javaClass.simpleName}"
                )
            }
            val start = (referencedElement as? PsiNameIdentifierOwner)?.nameIdentifier ?: referencedElement
            if (start.startOffset !in wantedReferences) {
                fail(
                    """file ${markupFile.name}: reference to ${referencedElement.javaClass.simpleName} at ${markupFile.lineCol(start.startOffset)}  was not supposed to exist"""
                )
            }
            start.startOffset
        }
        val foundTimes = foundReferences.groupBy { it }.mapValues { it.value.size }
        foundTimes.forEach { (pos, times) ->
            if (times != 1) {
                fail(
                    "file ${markupFile.name}: reference to position ${markupFile.lineCol(pos)} was found $times times"
                )
            }
        }
        val missedReferences = wantedReferences.filter { it !in foundReferences }.map(markupFile::lineCol)
        if (missedReferences.isNotEmpty()) {
            fail(
                "file ${markupFile.name}: references to positions $missedReferences were not found"
            )
        }
    }

    val externalReferenceFile = testDataPath / "external references.txt"
    val wantedExternalReferences = if (externalReferenceFile.exists()) externalReferenceFile.readLines().shouldNotContainDuplicates().toSet() else emptySet()
    val foundExternalReferences = externalReferences.map {
        var name: String? = config.externalReferenceToString(it)
        if (name == null) {
            val virtualFile = it.containingFile.virtualFile
            myFixture.openFileInEditor(virtualFile)
            val pos = myFixture.editor.offsetToLogicalPosition(it.startOffset)
            name = "unknown element of type ${it.javaClass.simpleName} in file ${virtualFile.presentableName} at position ${pos.line}:${pos.column}"
            fail("unexpected reference to $name")
        } else {
            if (name !in wantedExternalReferences) {
                fail("unexpected reference to $name")
            }
        }
        name
    }.toSet()
    val missingExternalReferences = wantedExternalReferences - foundExternalReferences
    if (missingExternalReferences.isNotEmpty()) {
        fail("didn't find references to external language elements $missingExternalReferences")
    }
}