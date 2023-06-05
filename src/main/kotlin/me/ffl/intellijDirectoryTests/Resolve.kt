package me.ffl.intellijDirectoryTests

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.startOffset
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotContainDuplicates
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

val resolveExecutor: KotestExecutor = {
    val projectFiles = testDataPath.loadProject()
    projectFiles.findCarets().forEach { caret ->
        withClue(if (caret.name == null) "Resolve nameless caret" else "Resolve caret ${caret.name}") {
            val reference = caret.file.findReferenceAt(caret.offset)
            val referencedElements = reference.multiResolve(false).map {
                it.element.shouldNotBeNull { "A ResolveResult had no element" }
            }
            val projectVirtualFiles = projectFiles.map { it.vFile }
            val (internalReferences, externalReferences) = referencedElements.partition { it.containingFile.virtualFile in projectVirtualFiles }
            projectFiles.forEach { markupFile ->
                val wantedReferences = markupFile.findWantedReferencePositions(caret)
                val foundReferences = internalReferences.filter {
                    it.containingFile.virtualFile == markupFile.vFile
                }.map { referencedElement ->
                    if (!reference.isReferenceTo(referencedElement)) {
                        fail(
                            "File ${markupFile.name}: Inconsistent isReferenceTo implementation in class ${reference.javaClass} referring to \"${referencedElement.text}\" of type ${referencedElement.javaClass.simpleName}"
                        )
                    }
                    val start = (referencedElement as? PsiNameIdentifierOwner)?.nameIdentifier ?: referencedElement
                    if (start.startOffset !in wantedReferences) {
                        fail(
                            """File ${markupFile.name}: Reference to ${referencedElement.javaClass.simpleName} at ${markupFile.lineCol(start.startOffset)}  was not supposed to exist"""
                        )
                    }
                    start.startOffset
                }
                val foundTimes = foundReferences.groupBy { it }.mapValues { it.value.size }
                foundTimes.forEach { (pos, times) ->
                    if (times != 1) {
                        fail(
                            "File ${markupFile.name}: Reference to position ${markupFile.lineCol(pos)} was found $times times"
                        )
                    }
                }
                val missedReferences = wantedReferences.filter { it !in foundReferences }.map(markupFile::lineCol)
                if (missedReferences.isNotEmpty()) {
                    fail(
                        "File ${markupFile.name}: References to positions $missedReferences were not found"
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
                    name = "Unknown element of type ${it.javaClass.simpleName} in file ${virtualFile.presentableName} at position ${pos.line}:${pos.column}"
                    fail("Unexpected reference to $name")
                } else {
                    if (name !in wantedExternalReferences) {
                        fail("Unexpected reference to $name")
                    }
                }
                name
            }.toSet()
            val missingExternalReferences = wantedExternalReferences - foundExternalReferences
            if (missingExternalReferences.isNotEmpty()) {
                fail("Didn't find references to external language elements $missingExternalReferences")
            }
        }
    }
}