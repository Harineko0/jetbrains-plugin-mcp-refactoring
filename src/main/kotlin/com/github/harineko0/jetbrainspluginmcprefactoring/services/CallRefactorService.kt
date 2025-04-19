package com.github.harineko0.jetbrainspluginmcprefactoring.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.JavaRefactoringFactory
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import java.nio.file.Paths
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err

/**
 * Data class to hold information about a single usage found.
 */
data class UsageInfo(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val usageTextSnippet: String // A small snippet of the line containing the usage
)

@Service(Service.Level.PROJECT)
class CallRefactorService(private val project: Project) {

    /**
     * Renames a PSI element identified by its position using code leading up to it.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param codeToSymbol The code from the start of the file up to the symbol to rename.
     * @param newName The new name for the element.
     * @return True if the rename operation was successful, false otherwise.
     */
    fun renameElement(filePath: String, codeToSymbol: String, newName: String): Result<Unit, String> {
        var err: Exception? = null
        ApplicationManager.getApplication().invokeAndWait {
            var targetFile: PsiFile? = null
            var targetElement: PsiElement? = null
            val offset = codeToSymbol.length

            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    err = IllegalStateException("Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction
                }

                val element = findElementAt(psiFile, offset) ?: run {
                    println("Error: Could not find element at offset $offset in file: $filePath")
                    err = IllegalStateException("Could not find element at offset $offset in file: $filePath")
                    return@runWriteCommandAction
                }

                targetFile = element.containingFile
                targetElement = element
            }

            if (targetFile != null && targetElement != null) {
                try {
                    println("Attempting to rename element '${targetElement!!.text}' at offset $offset to '$newName'")
                    val refactoring =
                        RefactoringFactory.getInstance(project).createRename(targetElement!!, newName)
                    refactoring.doRefactoring(refactoring.findUsages())
                    println("Rename successful for element at offset $offset in $filePath.")
                } catch (e: Exception) {
                    // Log the exception for better debugging
                    println("Rename failed: ${e.message}")
                    err = e
                }
            } else {
                println("Error: Target file or element is null after write command.")
                err = IllegalStateException("Target file or element is null after write command.")
            }
        }
        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error")
    }

    /**
     * Moves a PSI element identified by its position to a target directory.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param codeToSymbol The code from the start of the file up to the symbol to move.
     * @param targetDirectoryPath The absolute path to the target directory.
     * @return True if the move operation was successful, false otherwise.
     */
    fun moveElement(filePath: String, codeToSymbol: String, targetDirectoryPath: String): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction
                }

                val offset = codeToSymbol.length
                val element = findElementAt(psiFile, offset) ?: run {
                    println("Error: Could not find element at offset $offset in file: $filePath")
                    return@runWriteCommandAction
                }

                val targetVirtualFile =
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(targetDirectoryPath))
                val targetDirectory =
                    targetVirtualFile?.let { PsiManager.getInstance(project).findDirectory(it) } ?: run {
                        println("Error: Could not find target directory: $targetDirectoryPath")
                        return@runWriteCommandAction
                    }

                try {
                    println("Attempting to move element '${element.text}' at offset $offset to '$targetDirectoryPath'")

                    val javaFactory = JavaRefactoringFactory.getInstance(project)
                    javaFactory.createMoveClassesOrPackages(
                        arrayOf(element), SingleSourceRootMoveDestination(
                            PackageWrapper.create(PsiPackageImpl(psiFile.manager, targetDirectory.name)),
                            targetDirectory,
                        )
                    ).run()

                    println("Move successful for element at offset $offset in $filePath.")
                    success = true
                } catch (e: Exception) {
                    println("Move failed: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        return success
    }

    /**
     * Deletes a PSI element identified by its position.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param codeToSymbol The code from the start of the file up to the symbol to delete.
     * @return True if the delete operation was successful, false otherwise.
     */
    fun deleteElement(filePath: String, codeToSymbol: String): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction
                }

                val offset = codeToSymbol.length
                val element = findElementAt(psiFile, offset) ?: run {
                    println("Error: Could not find element at offset $offset in file: $filePath")
                    return@runWriteCommandAction
                }

                try {
                    println("Attempting to delete element '${element.text}' at offset $offset")
                    val refactoring =
                        RefactoringFactory.getInstance(project).createSafeDelete(arrayOf(element.containingFile))
                    // Safe delete
                    refactoring.run()
                    // Force delete
                    // refactoring.doRefactoring(refactoring.findUsages())
                    println("Delete successful for element at offset $offset in $filePath.")
                    success = true
                } catch (e: Exception) {
                    println("Delete failed: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        return success
    }

    /**
     * Finds usages of a PSI element identified by its name and optionally its line number.
     *
     * @param filePath The absolute path to the file containing the element definition.
     * @param codeToSymbol The code from the start of the file up to the symbol name.
     * @return A list of UsageInfo objects representing the found usages, or an empty list if none are found or an error occurs.
     */
    fun findUsage(filePath: String, codeToSymbol: String): List<UsageInfo> {
        // Use ReadAction as we are only reading information, not writing
        return ReadAction.compute<List<UsageInfo>, Throwable> {
            val psiFile = findPsiFile(filePath) ?: run {
                println("Error: Could not find PsiFile for path: $filePath")
                return@compute emptyList() // Use emptyList() for conciseness
            }

            val offset = codeToSymbol.length
            val element = findElementAt(psiFile, offset) ?: run {
                println("Error: Could not find element at offset $offset in file: $filePath")
                return@compute emptyList()
            }

            println("Searching usages of '${element.text}' (found at offset $offset) in $filePath")
            val usages = ReferencesSearch.search(element).findAll()
            println("Found ${usages.size} usages.")

            val usageInfos = mutableListOf<UsageInfo>()
            val documentManager = PsiDocumentManager.getInstance(project)

            for (reference in usages) {
                val usageElement = reference.element
                val usageFile = usageElement.containingFile
                val usageVirtualFile = usageFile?.virtualFile
                val usageFilePath = usageVirtualFile?.path ?: "Unknown File"
                val document = usageFile?.let { documentManager.getDocument(it) }

                if (document != null) {
                    val usageOffset = usageElement.textRange.startOffset // Renamed to avoid conflict
                    val line = document.getLineNumber(usageOffset) + 1 // 0-based to 1-based
                    val col = usageOffset - document.getLineStartOffset(line - 1) + 1 // Calculate column

                    // Get a snippet of the line containing the usage
                    val lineStartOffset = document.getLineStartOffset(line - 1)
                    val lineEndOffset = document.getLineEndOffset(line - 1)
                    val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

                    usageInfos.add(
                        UsageInfo(
                            filePath = usageFilePath,
                            lineNumber = line,
                            columnNumber = col,
                            usageTextSnippet = lineText.trim() // Trim whitespace from the snippet
                        )
                    )
                } else {
                    println("Warning: Could not get document for usage in file: $usageFilePath")
                    // Add usage with less info if document is unavailable
                    usageInfos.add(
                        UsageInfo(
                            filePath = usageFilePath,
                            lineNumber = -1, // Indicate unknown line/col
                            columnNumber = -1,
                            usageTextSnippet = usageElement.text ?: "N/A"
                        )
                    )
                }
            }
            usageInfos.forEach { usage ->
                println("  - ${usage.filePath}:${usage.lineNumber}:${usage.columnNumber} : ${usage.usageTextSnippet}")
            }

            usageInfos
        } // End ReadAction.compute
    }

    /**
     * Moves a file to a target directory.
     *
     * @param targetFilePath The absolute path to the file to move.
     * @param destDirectoryPath The absolute path to the destination directory.
     * @return True if the move operation was successful, false otherwise.
     */
    fun moveFile(targetFilePath: String, destDirectoryPath: String): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(targetFilePath) ?: run {
                    println("Error: Could not find PsiFile for path: $targetFilePath")
                    return@runWriteCommandAction
                }

                val targetVirtualDir =
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(destDirectoryPath))
                val targetDirectory =
                    targetVirtualDir?.let { PsiManager.getInstance(project).findDirectory(it) } ?: run {
                        println("Error: Could not find target directory: $destDirectoryPath")
                        return@runWriteCommandAction
                    }

                try {
                    println("Attempting to move file '$targetFilePath' to '$destDirectoryPath'")
                    // Use MoveFilesOrDirectoriesHandler for moving files

                    // check if target is a directory or a file
//                    if (targetDirectory.virtualFile.isDirectory) {
//                        println("Target is a directory.")
//                    } else {
//                        println("Target is a file.")
//                    }

                    // MoveFilesOrDirectoriesUtil.doMoveFile
                    MoveFilesOrDirectoriesUtil.doMove(
                        project,
                        arrayOf(psiFile),
                        arrayOf(targetDirectory),
                        // MoveCallback is interface.
                        object : MoveCallback {
                            override fun refactoringCompleted() {
                                success = true
                                println("Move successful for file $targetFilePath.")
                            }
                        }
                    )
                } catch (e: Exception) {
                    println("Move file failed: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        return success
    }

    /**
     * Renames a file.
     *
     * @param targetFilePath The absolute path to the file to rename.
     * @param newName The new name for the file (including extension).
     * @return True if the rename operation was successful, false otherwise.
     */
    fun renameFile(targetFilePath: String, newName: String): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(targetFilePath) ?: run {
                    println("Error: Could not find PsiFile for path: $targetFilePath")
                    return@runWriteCommandAction
                }

                try {
                    println("Attempting to rename file '$targetFilePath' to '$newName'")
                    // Use RefactoringFactory to rename the PsiFile itself
                    val refactoring = RefactoringFactory.getInstance(project).createRename(psiFile, newName)
                    refactoring.doRefactoring(refactoring.findUsages())
                    println("Rename successful for file $targetFilePath.")
                    success = true
                } catch (e: Exception) {
                    println("Rename file failed: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        return success
    }

    /**
     * Finds the PsiFile corresponding to the given file path.
     * Requires read access.
     */
    private fun findPsiFile(filePath: String): PsiFile? {
        return ReadAction.compute<PsiFile?, Throwable> {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath))
            virtualFile?.let {
                if (it.isValid) {
                    PsiManager.getInstance(project).findFile(it)
                } else {
                    println("Warning: VirtualFile is invalid for path: $filePath")
                    null
                }
            }
        }
    }

    /**
     * Finds the PsiElement at the specified offset within a PsiFile.
     * Requires read access.
     */
    private fun findElementAt(psiFile: PsiFile, offset: Int): PsiElement? {
        return ReadAction.compute<PsiElement?, Throwable> {
            val element = psiFile.findElementAt(offset)
            if (element == null) {
                println("Warning: No element found at offset $offset in file ${psiFile.virtualFile?.path}")
            }
            // Often findElementAt returns whitespace or punctuation. Try to get the parent identifier/named element.
            element?.parentOfType<PsiNameIdentifierOwner>(withSelf = true) ?: element
        }
    }

    // Helper extension function to get parent of a specific type
    inline fun <reified T : PsiElement> PsiElement.parentOfType(withSelf: Boolean): T? {
        return PsiTreeUtil.getParentOfType(this, T::class.java, !withSelf)
    }
}
