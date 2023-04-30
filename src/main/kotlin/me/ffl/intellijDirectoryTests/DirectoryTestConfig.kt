package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.nio.file.Path
import kotlin.io.path.Path

data class DirectoryTestConfig(
    val kotestExecutors: Map<String, KotestExecutor>,
    val needsHeavyTestRunner: Set<String>,
    val needsNoWriteAction: Set<String>,
    val testDataPath: Path,
    val knownIntentions: List<IntentionAction>,
    /**
     * If true (by default this is false), the files specifying expected parser output is overridden with the actual output.
     * This is useful, if you prefer to use `git diff` or intellij's diff view instead of clicking through all tests.
     * Obviously, you should `git restore` the files, if the output did not change in the intended way.
     */
    val overrideParserOutput: Boolean,
    /**
     * This function is used to check, whether a reference to a psi element outside the project,
     * corresponds to an entry of `external references.txt`.
     * This has cannot be implemented by intellijDirectoryTests, because it is specific to the tested language.
     * If the function returns null, the PsiElement cannot correspond to any entry.
     * This can be used f.e. if the element has no name or is in a file of unknown language.
     */
    val externalReferenceToString: (PsiElement) -> String?,
    val projectDescriptor: LightProjectDescriptor?,
    /**
     * Configure kotest to continue on assertion errors and show all errors at last, instead of stopping at the first error.
     */
    val softAssertByDefault: Boolean,
) {
    internal val knownIntentionMap = knownIntentions.associateBy { it.javaClass.simpleName.simplifyIntentionName() }

    internal fun createContext(
        testName: String,
        testDataPath: Path,
        myFixture: CodeInsightTestFixture
    ): KotestExecutorContext {
        return KotestExecutorContext(testName, testDataPath, myFixture, this)
    }

    companion object {
        private val defaultKotestExecutors: Map<String, KotestExecutor> = mapOf(
            "rename" to renameExecutor,
            "parser" to parserExecutor,
            "documentation" to documentationExecutor,
            "find usages" to findUsagesExecutor,
            "actions" to actionExecutor,
            "resolve" to resolveExecutor,
            "hints" to hintsExecutor,
            "inline" to inlineExecutor,
            "completion" to completionExecutor,
        )
        private val defaultTestDataPath = Path("src/test/testData")
        val denyAllExternalReferences: (PsiElement) -> String? = { null }
        val default = DirectoryTestConfig(
            defaultKotestExecutors,
            emptySet(),
            emptySet(),
            defaultTestDataPath,
            emptyList(),
            false,
            denyAllExternalReferences,
            null,
            true,
        )

        fun String.simplifyIntentionName() = removeSuffix("Action").removeSuffix("Intention")
    }
}
