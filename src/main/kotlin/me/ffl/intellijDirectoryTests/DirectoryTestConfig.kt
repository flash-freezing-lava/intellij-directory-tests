package me.ffl.intellijDirectoryTests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.nio.file.Path
import kotlin.io.path.Path

data class DirectoryTestConfig(
    val kotestExecutors: Map<String, KotestExecutor>,
    /**
     * Set of test executors, that require actual files in /tmp, for example because they run external tools on the files.
     * For other test executors, lighter in-memory files are used.
     */
    val needsHeavyTestRunner: Set<String>,
    val testDataPath: Path,
    val knownIntentions: List<IntentionAction>,
    /**
     * If true (by default this is false), the files specifying expected parser output is overridden with the actual output.
     * This is useful, if you prefer to use `git diff` or intellij's diff view, to check the correctness of changed parser output.
     * Obviously, you should `git restore` the files, if the output did not change in the intended way.
     */
    val overrideParserOutput: Boolean,
    /**
     * If true (by default this is false), the files specifying expected documentation output is overridden with the actual output.
     * This is useful, if you prefer to use `git diff` or intellij's diff view, to check the correctness of changed parser output.
     * Obviously, you should `git restore` the files, if the output did not change in the intended way.
     */
    val overrideDocumentationOutput: Boolean,
    /**
     * This function is used to check, whether a reference to a psi element outside the project,
     * corresponds to an entry of `external references.txt`.
     * This cannot be implemented by intellijDirectoryTests, because it is specific to the tested language.
     * If the function returns null, the PsiElement cannot correspond to any entry.
     */
    val externalReferenceToString: (PsiElement) -> String?,
    val projectDescriptor: LightProjectDescriptor?,
    /**
     * Configure kotest to continue on assertion errors and show all errors, instead of stopping at the first error.
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
            "executed completion" to executedCompletionExecutor,
            "highlighting" to highlightExecutor,
        )
        private val defaultTestDataPath = Path("src/test/testData")
        val denyAllExternalReferences: (PsiElement) -> String? = { null }
        val default = DirectoryTestConfig(
            kotestExecutors = defaultKotestExecutors,
            needsHeavyTestRunner = emptySet(),
            testDataPath = defaultTestDataPath,
            knownIntentions = emptyList(),
            overrideParserOutput = false,
            overrideDocumentationOutput = false,
            externalReferenceToString = denyAllExternalReferences,
            projectDescriptor = null,
            softAssertByDefault = true,
        )

        fun String.simplifyIntentionName() = removeSuffix("Action").removeSuffix("Intention")
    }
}
