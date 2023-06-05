package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.usageView.UsageInfo
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.reflect.KClass

class MarkupFile(
    private val myFixture: CodeInsightTestFixture,
    private val _vFile: VirtualFile?,
    private val markup: String,
    val name: String
) {
    private val refPattern = "<(?<type>ref|caret|usage|parse-error)(?<name> [a-zA-Z_0-9]*)?>"
    private val points: List<MarkupPoint>
    val code: String
    val vFile: VirtualFile
        get() = _vFile ?: error("tried to retrieve non-backed virtual file")

    constructor(
         myFixture: CodeInsightTestFixture,
         vFile: VirtualFile,
        markup: String,
    ): this(myFixture, vFile, markup, vFile.name)

    init {
        val occurrences = Regex(refPattern).findAll(markup).toList()
        points = occurrences.map { matchResult ->
            MarkupPoint(
                matchResult.range.first,
                matchResult.value.length,
                matchResult.groups["type"]!!.value, // garanteed not-null, because the type regex is not optional
                matchResult.groups["name"]?.value
            )
        }
        code = constructCode()
        if (_vFile != null)
            VfsUtil.saveText(vFile, code)
    }

    private fun constructCode(): String {
        val codeBuilder = StringBuilder()
        var startPos = 0
        for (point in points) {
            codeBuilder.append(markup, startPos, point.pos)
            startPos = point.pos + point.len
        }
        codeBuilder.append(markup, startPos, markup.length)
        return codeBuilder.toString()
    }

    private fun translateMarkupToFile(pos: Int): Int {
        var res = pos
        for (point in points) {
            if (point.pos > pos) break
            res = (pos - point.len).coerceAtLeast(point.pos) - (pos - res)
        }
        return res
    }

    private fun translateFileToMarkup(pos: Int): Int {
        var res = pos
        for (point in points) {
            if (point.pos > pos) break
            res += point.len
        }
        return res
    }

    fun findCarets(): List<FoundCaret> {
        return points
            .filter { it.type == "caret" }
            .map { FoundCaret(this, translateMarkupToFile(it.pos), it.name) }
    }

    fun findWantedReferencePositions(caret: FoundCaret): List<Int> {
        return points.filter { it.type == "ref" && it.name == caret.name }.map { translateMarkupToFile(it.pos) }
    }

    fun findWantedUsagesPositions(caret: FoundCaret): List<Int> {
        return points.filter { it.type == "usage" && it.name == caret.name }.map { translateMarkupToFile(it.pos) }
    }

    @JvmName("findReferenceAtWithDefaultGeneric")
    fun findReferenceAt(offset: Int): PsiPolyVariantReference = findReferenceAt(PsiPolyVariantReference::class, offset)

    private fun <T : PsiPolyVariantReference> findReferenceAt(clazz: KClass<T>, offset: Int): T {
        myFixture.openFileInEditor(vFile)
        val reference = myFixture.file.findReferenceAt(offset).shouldNotBeNull {
            "no reference found at offset ${lineCol(offset)}"
        }
        assert(clazz.isInstance(reference)) { "found reference of type ${reference.javaClass} but expected type $clazz" }
        @Suppress("UNCHECKED_CAST")
        return reference as T
    }

    fun findUsagesAt(offset: Int): FindUsagesResult {
        myFixture.openFileInEditor(vFile)
        myFixture.editor.caretModel.moveToOffset(offset)
        val (declaration, _) = com.intellij.model.psi.impl.targetDeclarationAndReferenceSymbols(myFixture.psiManager.findFile(vFile)!!, offset)
        assert(declaration.isNotEmpty()) {
            "file $name: no declaration found at ${lineCol(offset)}"
        }
        val symbol = declaration.first()
        val element = PsiSymbolService.getInstance().extractElementFromSymbol(symbol).shouldNotBeNull {
            "file $name: no psi found for declaration $symbol at ${lineCol(offset)}"
        }
        return FindUsagesResult(element, myFixture.findUsages(element))
    }

    fun assertExactParsingErrors() {
        myFixture.openFileInEditor(vFile)
        val foundErrors = myFixture.file.descendantsOfType<PsiErrorElement>().toList()
        val expectedErrors = points.filter { it.type == "parse-error" }
        val unexpectedErrors = foundErrors.filter { foundError ->
            expectedErrors.none { expectedError -> foundError.startOffset == translateMarkupToFile(expectedError.pos) }
        }
        check(unexpectedErrors.isEmpty()) {
            "file $name: parsing failed unexpectedly with errors ${unexpectedErrors.map { """"${it.errorDescription}" at ${lineCol(it.startOffset)}""" }}"
        }
        val notFoundExpectedErrors = expectedErrors.filter { expectedError ->
            foundErrors.none { foundError -> foundError.startOffset == translateMarkupToFile(expectedError.pos) }
        }
        check(notFoundExpectedErrors.isEmpty()) {
            "file $name: parsing succeeded unexpectedly at ${notFoundExpectedErrors.map { lineCol(translateMarkupToFile(it.pos)) }}"
        }
    }

    val expectsParseErrors: Boolean get() = points.any { it.type == "parse-error" }

    fun renameElementAt(offset: Int, newName: String) {
        myFixture.openFileInEditor(vFile)
        myFixture.editor.caretModel.moveToOffset(offset)
        // TODO maybe use targetDeclarationAndReferenceSymbols like in findUsagesAt
        val element = TargetElementUtil.findTargetElement(
            myFixture.editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        ).shouldNotBeNull {
            "file $name: no psi element to rename found at ${lineCol(offset)}"
        }
        myFixture.renameElement(element, newName)
    }

    fun getDocumentationAt(offset: Int): String? {
        myFixture.openFileInEditor(vFile)
        val originalElement = myFixture.file.findElementAt(offset).shouldNotBeNull { "no element found at caret" }
        // while deprecated, this is still the recommended way from https://plugins.jetbrains.com/docs/intellij/documentation-test.html#define-a-test-method
        // unless JetBrains tells how to replace it, I will stick to it
        val target = DocumentationManager.getInstance(myFixture.project)
            .findTargetElement(myFixture.editor, originalElement.containingFile, originalElement)
        val documentationProvider = DocumentationManager.getProviderFromElement(target)
        return documentationProvider.generateDoc(target, originalElement)
    }

    fun getHintAt(offset: Int): String? {
        myFixture.openFileInEditor(vFile)
        val highlightingPass = InlayHintsPassFactory().createHighlightingPass(myFixture.file, myFixture.editor)
            ?: throw AssertionError("no highlighting pass was constructed")
        highlightingPass.doCollectInformation(EmptyProgressIndicator())
        highlightingPass.doApplyInformationToEditor()
        val hints = myFixture.editor.inlayModel.getInlineElementsInRange(offset, offset).map {
            it.renderer.toString()
        }
        if (hints.isEmpty()) return null
        withClue({
            "file $name: there should be only one hint at ${lineCol(offset)}"
        }) {
            hints shouldHaveSize 1
        }
        return hints[0]
    }

    fun inlineAt(offset: Int) {
        myFixture.openFileInEditor(vFile)
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction("Inline")
    }

    fun lineCol(offset: Int): LineCol {
        var col = translateFileToMarkup(offset)
        var lineNr = 0
        markup.lineSequence().forEach {
            if (col > it.length) {
                col -= it.length + 1
                lineNr += 1
            } else {
                return LineCol(lineNr + 1, col + 1)
            }
        }
        error("offset not in file")
    }

    fun executeIntentionAt(action: IntentionAction, offset: Int) {
        myFixture.openFileInEditor(vFile)
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.launchAction(action)
    }

    fun executeCompletionAt(offset: Int): Array<out LookupElement>? {
        myFixture.openFileInEditor(vFile)
        myFixture.editor.caretModel.moveToOffset(offset)
        return myFixture.completeBasic()
    }

    /**
     * @param pos Position in the markup file, **not** in the unescaped code
     */
    private data class MarkupPoint(val pos: Int, val len: Int, val type: String, val name: String?)

    companion object {
        fun List<MarkupFile>.findCarets(): Collection<FoundCaret> {
            val carets = flatMap { file -> file.findCarets() }.groupBy { it.name }
            val caretMap = carets.mapValues {
                checkNotNull(it.value.singleOrNull()) {
                    "The name ${it.key} was used for multiple carets. Caret names must be unique."
                }
            }
            check(caretMap.isNotEmpty()) {
                "No caret found in any file."
            }
            return caretMap.values
        }
    }
}

data class LineCol(val line: Int, val col: Int) {
    override fun toString(): String {
        return "$line:$col"
    }
}

data class FindUsagesResult(val definitionElement: PsiElement, val usages: Collection<UsageInfo>)

data class FoundCaret(val file: MarkupFile, val offset: Int, val name: String?)
