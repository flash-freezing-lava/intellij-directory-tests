package me.ffl.intellijDirectoryTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

fun KotestExecutorContext.loadNonMarkupProject(path: Path): List<VirtualFile> = runWriteAction {
    val referenceRootPath = path
    val root = myFixture.tempDirFixture.getFile(".")!!

    fun markupFile(path: Path): List<VirtualFile> {
        val relPath = referenceRootPath.relativize(path)
        return if (path.isDirectory()) {
            // Don't create project dir. It already exists.
            if (referenceRootPath != path) VfsTestUtil.createDir(root, relPath.toString())
            path.listDirectoryEntries().flatMap(::markupFile)
        } else {
            val file = VfsTestUtil.createFile(root, relPath.toString())
            file.writeText(path.readText())
            listOf(file)
        }
    }

    return@runWriteAction markupFile(referenceRootPath)
}

val highlightExecutor: KotestExecutor = {
    val vFiles = loadNonMarkupProject(testDataPath)
    vFiles.forEach {
        myFixture.configureFromExistingVirtualFile(it)
        myFixture.checkHighlighting(true, true, true, false)
    }
}