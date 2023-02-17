package me.ffl.intellijDirectoryTests

import com.intellij.refactoring.suggested.startOffset
import me.ffl.intellijDirectoryTests.MarkupFile.Companion.findCaret

val findUsagesExecutor: KotestExecutor = {
    val beforeFiles = testDataPath.loadProject()
    val caret = beforeFiles.findCaret()
    val usageResult = caret.file.findUsagesAt(caret.offset)

    val usages = usageResult.usages
    usages.forEach { it.element.shouldNotBeNull { "usage of null found" } }
    beforeFiles.forEach { markupFile ->
        val fileName = markupFile.name
        val wantedUsages = markupFile.findWantedUsagesPositions()
        val usingElementsInThisFile = usages.filter { it.element!!.containingFile.virtualFile == markupFile.vFile }
        val foundReferences = usingElementsInThisFile.map { usage ->
            val usingElement = usage.element
            val start = +usingElement!!.startOffset + (usage.rangeInElement?.startOffset ?: 0)
            val lineCol = markupFile.lineCol(start)
            val reference = usingElement.reference
            if (reference == null) {
                fail("""file $fileName: using element "${usingElement.text}"($lineCol) of type ${usingElement.javaClass.simpleName} had no reference""")
            } else {
                if (!reference.isReferenceTo(usageResult.definitionElement)) {
                    fail("""file $fileName: inconsistent isReferenceTo implementation in class ${reference.javaClass} in reference from "${usingElement.text}"($lineCol) of type ${usingElement.javaClass.simpleName}""")
                }
                if (start !in wantedUsages) {
                    fail("""file $fileName: reference from "${usingElement.text}"(${lineCol}) of type ${usingElement.javaClass.simpleName} was not supposed to exist""")
                }
            }
            start
        }
        val foundTimes = foundReferences.groupBy { it }.mapValues { it.value.size }
        foundTimes.forEach { (pos, times) ->
            if (times != 1) {
                fail("file ${markupFile.name}: reference from position ${markupFile.lineCol(pos)} was found $times times")
            }
        }
        val missedUsages = wantedUsages.filter { it !in foundReferences }.map { markupFile.lineCol(it) }
        if (missedUsages.isNotEmpty()) {
            fail("file $fileName: references from positions $missedUsages were not found")
        }
    }
}

