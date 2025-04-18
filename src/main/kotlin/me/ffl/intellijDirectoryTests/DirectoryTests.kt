package me.ffl.intellijDirectoryTests

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UITestUtil.replaceIdeEventQueueSafely
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.collectOrThrow
import io.kotest.assertions.errorCollector
import io.kotest.assertions.failure
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.FunSpecContainerScope
import java.nio.file.Path
import kotlin.io.path.*

fun <T: Any> T?.shouldNotBeNull(message: () -> String): T {
    assert(this != null, message)
    return this!!
}

private fun getModuleBuilderClass(): Class<out ModuleFixtureBuilder<*>> =
    EmptyModuleFixtureBuilder::class.java

typealias KotestExecutor = KotestExecutorContext.() -> Unit

@Suppress("unused")
abstract class DirectoryTests(config: DirectoryTestConfig = DirectoryTestConfig.default): FunSpec({
    fun createTempDirTestFixture(): TempDirTestFixture {
        val policy = IdeaTestExecutionPolicy.current()
        return if (policy != null) policy.createTempDirTestFixture() else LightTempDirTestFixtureImpl(true)
    }

    var myFixture: CodeInsightTestFixture? = null
    fun setupTest(testName: String, needsHeavyTestRunner: Boolean) {
        // I don't know why myFixture is sometimes non-null here, but this serves as a work-around, because I don't want to spend too much time debugging this
        myFixture?.tearDown()

        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()

        if (needsHeavyTestRunner) {
            val projectBuilder = fixtureFactory.createFixtureBuilder(testName)
            myFixture = fixtureFactory.createCodeInsightFixture(projectBuilder.fixture)
            val moduleFixtureBuilder = projectBuilder.addModule(getModuleBuilderClass())
            moduleFixtureBuilder.addSourceContentRoot(myFixture!!.tempDirPath)
        } else {
            val fixtureBuilder = fixtureFactory.createLightFixtureBuilder(config.projectDescriptor, testName)
            val fixture = fixtureBuilder.fixture
            myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, createTempDirTestFixture())
        }
        myFixture!!.setUp()
        // Check that all plugins loaded correctly
        runBlocking {
            PluginManagerCore.initPluginFuture.await()
            assertSoftly {
                PluginManagerCore.plugins.map { it.pluginId }.forEach { pluginId ->
                    val loadingError = PluginManagerCore.getLoadingError(pluginId)
                    if (loadingError != null) {
                        errorCollector.collectOrThrow(failure("Failed to load plugin $pluginId: ${loadingError.shortMessage}\n${loadingError.detailedMessage}"))
                    }
                }
            }
        }
    }

    afterTest {
        val oldFixture = myFixture
        // first set this to null, to make sure, this is null, even if tearDown fails
        myFixture = null
        oldFixture?.tearDown()
    }

    val testDataDir = config.testDataPath
    val onlyPath = testDataDir / "only.txt"
    val only = if (onlyPath.exists()) onlyPath.readText().trim() else null
    if (!only.isNullOrEmpty()) {
        test("Not all tests were run") {
            throw AssertionError("Some tests were disabled by only.txt")
        }
    }
    fun execute(executor: KotestExecutor, context: KotestExecutorContext) {
        if (config.softAssertByDefault) {
            assertSoftly {
                executor(context)
            }
        } else {
            executor(context)
        }
    }
    testDataDir.listDirectoryEntries().filter { !it.toString().endsWith(".txt") }.sorted().forEach { executorTestDataDir ->
        val executorName = executorTestDataDir.name
        val executor = config.kotestExecutors[executorName]
        if (executor == null) {
            test(executorName) {
                throw AssertionError("No executor found for this directory")
            }
        } else {
            context(executorName) {
                suspend fun FunSpecContainerScope.processDir(dir: Path) {
                    if ((dir / "context.txt").exists()) {
                        dir.listDirectoryEntries().filter { it.name != "context.txt" }.sorted().forEach {
                            context(it.name) {
                                processDir(it)
                            }
                        }
                    } else {
                        dir.listDirectoryEntries().sorted().forEach { testDataPath ->
                            val isDisable = only != null && !testDataPath.toString().endsWith(only) && !testDataDir.relativize(testDataPath).toString().startsWith(only)
                            val disableText = if (isDisable) "!" else ""
                            val testName = disableText + testDataPath.name
                            test(testName) {
                                setupTest(testName, executorName in config.needsHeavyTestRunner)
                                val context = config.createContext(testName, testDataPath, myFixture!!)
                                // don't create application in EDT
                                TestApplicationManager.getInstance()
                                val policy = IdeaTestExecutionPolicy.current()
                                val runInDispatchThread = policy?.runInDispatchThread() ?: true
                                if (runInDispatchThread) {
                                    // copied from UsefulTestCase::runBare
                                    replaceIdeEventQueueSafely()
                                    runInEdtAndWait {
                                        execute(executor, context)
                                    }
                                } else {
                                    execute(executor, context)
                                }
                            }
                        }
                    }
                }

                processDir(executorTestDataDir)
            }
        }
    }
})