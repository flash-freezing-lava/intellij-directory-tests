package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.InlayTreeSinkImpl
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.SyntaxTraverser
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
    private val refPattern = "<(?<type>ref|caret|usage|parse-error)(?<name> [a-zA-Z_0-9]+)?>"
    private val points: List<MarkupPoint>
    val code: String
    val vFile: VirtualFile
        get() = _vFile ?: error("Tried to retrieve non-backed virtual file")

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
                matchResult.groups["type"]!!.value, // guaranteed not-null, because the type regex is not optional
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
            if (point.pos > res) break
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
    fun findReferenceAt(offset: Int): PsiPolyVariantReference? = findReferenceAt(PsiPolyVariantReference::class, offset)

    private fun getInjectedFileAnsOffset(offset: Int): Pair<PsiFile, Int> {
        myFixture.openFileInEditor(vFile)
        // Commit is necessary for InjectedLanguageManager.findInjectedElementAt to have defined behavior
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
        val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.project)
        var injectedFile: PsiFile = myFixture.file
        var injectedOffset: Int = offset
        var lastHost: PsiLanguageInjectionHost? = null
        while (true) {
            val injectedElement = injectedLanguageManager.findInjectedElementAt(myFixture.file, offset) ?: break
            val host = injectedLanguageManager.getInjectionHost(injectedElement) ?: break
            if (host == lastHost) break
            val escapedOffsetInInjectedHost = injectedOffset - host.textOffset
            val (file, shreds) = buildList<Pair<PsiFile, List<Shred>>> {
                injectedLanguageManager.enumerate(host) { file, shreds ->
                    this += file to shreds
                }
            }.firstOrNull() ?: break
            injectedFile = file
            val shred = shreds.single { shred ->
                escapedOffsetInInjectedHost in shred.rangeInsideHost
            }
            // This out-commented code could determine the corresponding unescaped code
            // However, since PsiElement.text currently gives the escaped text, despite the parser analyzing the unescaped,
            // it is not needed, and we should use the offset in the escaped text.
//            val unescaped = StringBuilder()
//            escaper.decode(TextRange(shred.rangeInsideHost.startOffset, escapedOffsetInInjectedHost), unescaped)
            injectedOffset = shred.prefix.length + escapedOffsetInInjectedHost
            lastHost = host
        }
        return injectedFile to injectedOffset
    }

    private fun <T : PsiPolyVariantReference> findReferenceAt(clazz: KClass<T>, offset: Int): T? {
        val (injectedFile, injectedOffset) = getInjectedFileAnsOffset(offset)
        val reference = injectedFile.findReferenceAt(injectedOffset) ?: return null
        assert(clazz.isInstance(reference)) { "File $name: Found reference of type ${reference.javaClass} at ${lineCol(offset)} but expected type $clazz" }
        @Suppress("UNCHECKED_CAST")
        return reference as T
    }

    fun findUsagesAt(offset: Int): FindUsagesResult {
        val (injectedFile, injectedOffset) = getInjectedFileAnsOffset(offset)
        val (declaration, _) = com.intellij.model.psi.impl.targetDeclarationAndReferenceSymbols(injectedFile, injectedOffset)
        assert(declaration.isNotEmpty()) {
            "File $name: No declaration found at ${lineCol(offset)}"
        }
        val symbol = declaration.first()
        val element = PsiSymbolService.getInstance().extractElementFromSymbol(symbol).shouldNotBeNull {
            "File $name: No psi found for declaration $symbol at ${lineCol(offset)}"
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
            "File $name: Parsing failed unexpectedly with errors ${unexpectedErrors.map { """"${it.errorDescription}" at ${lineCol(it.startOffset)}""" }}"
        }
        val notFoundExpectedErrors = expectedErrors.filter { expectedError ->
            foundErrors.none { foundError -> foundError.startOffset == translateMarkupToFile(expectedError.pos) }
        }
        check(notFoundExpectedErrors.isEmpty()) {
            "File $name: Parsing succeeded unexpectedly at ${notFoundExpectedErrors.map { lineCol(translateMarkupToFile(it.pos)) }}"
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
            "File $name: No psi element to rename found at ${lineCol(offset)}"
        }
        myFixture.renameElement(element, newName)
    }

    fun getDocumentationAt(offset: Int): String? {
        val (injectedFile, injectedOffset) = getInjectedFileAnsOffset(offset)
        val originalElement = injectedFile.findElementAt(injectedOffset).shouldNotBeNull { "no element found at caret" }
        // while deprecated, this is still the recommended way from https://plugins.jetbrains.com/docs/intellij/documentation-test.html#define-a-test-method
        // unless JetBrains tells how to replace it, I will stick to it
        val target = DocumentationManager.getInstance(myFixture.project)
            .findTargetElement(myFixture.editor, originalElement.containingFile, originalElement)
        val documentationProvider = DocumentationManager.getProviderFromElement(target)
        return documentationProvider.generateDoc(target, originalElement)
    }

    fun collectDeclarativeInlayHints(): Map<Int, List<String>> {
        val providerInfos = InlayHintsProviderFactory.getProvidersForLanguage(myFixture.file.language)
        val file = myFixture.file!!
        val editor = myFixture.editor
        val data = providerInfos.flatMap { providerInfo ->
            val collector = providerInfo.provider.createCollector(file, editor) ?: return@flatMap emptyList()
            val treeSink = InlayTreeSinkImpl(
                providerInfo.providerId,
                emptyMap(),
                isInPreview = false,
                providerIsDisabled = false,
                providerClass = providerInfo.provider.javaClass,
                sourceId = "DIR_TEST_SOURCE_ID",
            )
            when (collector) {
                is OwnBypassCollector -> {
                    collector.collectHintsForFile(file, treeSink)
                }
                is SharedBypassCollector -> {
                    SyntaxTraverser.psiTraverser(file).forEach {
                        collector.collectFromElement(it, treeSink)
                    }
                }
            }
            treeSink.finish()
        }
        return data.groupBy({ inlayData ->
            val fileOffset = when (val pos = inlayData.position) {
                is EndOfLinePosition -> editor.logicalPositionToOffset(LogicalPosition(pos.line + 1, 0))
                is InlineInlayPosition -> pos.offset
            }
            fileOffset
        }) { inlayData ->
            val strings = (0..<inlayData.tree.size).mapNotNull { inlayData.tree.getDataPayload(it.toByte()) as? String }
            strings.joinToString("")
        }
    }

    fun getHintAt(offset: Int): String? {
        myFixture.openFileInEditor(vFile)
        val declarativeHints = collectDeclarativeInlayHints()[offset].orEmpty()
        myFixture.doHighlighting()
        val inlays = myFixture.editor.inlayModel.getBlockElementsInRange(offset, offset) +
                myFixture.editor.inlayModel.getInlineElementsInRange(offset, offset) +
                myFixture.editor.inlayModel.getAfterLineEndElementsInRange(offset, offset)
        val otherHints = inlays.mapNotNull {
            if (it.renderer is DeclarativeInlayRenderer) null
            else it.renderer.toString()
        }
        val hints = declarativeHints + otherHints
        if (hints.isEmpty()) return null
        withClue({
            "File $name: There should be only one hint at ${lineCol(offset)}"
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
        error("Offset not in file")
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
                "No caret found in any file"
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
