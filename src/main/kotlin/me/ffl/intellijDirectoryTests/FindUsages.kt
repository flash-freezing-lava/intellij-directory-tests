package me.ffl.intellijDirectoryTests

import com.intellij.refactoring.suggested.startOffset
import io.kotest.assertions.withClue
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCarets

val findUsagesExecutor: KotestExecutor = {
    val beforeFiles = testDataPath.loadProject()
    beforeFiles.findCarets().forEach { caret ->
        withClue(if (caret.name == null) "Find usages for nameless caret" else "Find usages for caret ${caret.name}") {
            val usageResult = caret.file.findUsagesAt(caret.offset)

            val usages = usageResult.usages
            usages.forEach { it.element.shouldNotBeNull { "Usage of null found" } }
            beforeFiles.forEach { markupFile ->
                val fileName = markupFile.name
                val wantedUsages = markupFile.findWantedUsagesPositions(caret)
                val usingElementsInThisFile = usages.filter { it.element!!.containingFile.virtualFile == markupFile.vFile }
                val foundReferences = usingElementsInThisFile.map { usage ->
                    val usingElement = usage.element
                    val start = usingElement!!.startOffset + (usage.rangeInElement?.startOffset ?: 0)
                    val lineCol = markupFile.lineCol(start)
                    val references = usingElement.references
                    if (references.isEmpty()) {
                        fail("""File $fileName: Using element "${usingElement.text}"($lineCol) of type ${usingElement.javaClass.simpleName} had no reference""")
                    } else {
                        if (references.none { it.isReferenceTo(usageResult.definitionElement) }) {
                            fail("""File $fileName: Inconsistent isReferenceTo implementation in class ${references.javaClass} in reference from "${usingElement.text}"($lineCol) of type ${usingElement.javaClass.simpleName}""")
                        }
                        if (start !in wantedUsages) {
                            fail("""File $fileName: Reference from "${usingElement.text}"(${lineCol}) of type ${usingElement.javaClass.simpleName} was not supposed to exist""")
                        }
                    }
                    start
                }
                val foundTimes = foundReferences.groupBy { it }.mapValues { it.value.size }
                foundTimes.forEach { (pos, times) ->
                    if (times != 1) {
                        fail("File ${markupFile.name}: Reference from position ${markupFile.lineCol(pos)} was found $times times")
                    }
                }
                val missedUsages = wantedUsages.filter { it !in foundReferences }.map { markupFile.lineCol(it) }
                if (missedUsages.isNotEmpty()) {
                    fail("File $fileName: References from positions $missedUsages were not found")
                }
            }
        }
    }
}

