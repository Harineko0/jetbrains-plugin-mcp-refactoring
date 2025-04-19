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
    fun moveElement(filePath: String, codeToSymbol: String, targetDirectoryPath: String): Result<Unit, String> {
        var err: Exception? = null
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
                    ).run() // Note: This might not throw exceptions directly for all failure cases.

                    println("Move initiated for element at offset $offset in $filePath.")
                    // Assuming success if no immediate exception is thrown by run()
                } catch (e: Exception) {
                    val msg = "Move failed: ${e.message}"
                    println(msg)
                    err = e // Store the exception
                }
            }
        }
        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error during move element")
    }

    /**
     * Deletes a PSI element identified by its position.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param codeToSymbol The code from the start of the file up to the symbol to delete.
     * @return Result indicating success (Ok) or failure (Err) with an error message.
     */
    fun deleteElement(filePath: String, codeToSymbol: String): Result<Unit, String> {
        var err: Exception? = null
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    val msg = "Error: Could not find PsiFile for path: $filePath"
                    println(msg)
                    err = IllegalStateException(msg)
                    return@runWriteCommandAction
                }

                val offset = codeToSymbol.length
                val element = findElementAt(psiFile, offset) ?: run {
                    val msg = "Error: Could not find element at offset $offset in file: $filePath"
                    println(msg)
                    err = IllegalStateException(msg)
                    return@runWriteCommandAction
                }

                // Check if an error occurred before proceeding
                if (err != null) return@runWriteCommandAction

                try {
                    println("Attempting to delete element '${element.text}' at offset $offset")
                    // Using direct deletion as it might be more appropriate for a single element
                    element.delete()
                    println("Delete successful for element at offset $offset in $filePath.")
                } catch (e: Exception) {
                    val msg = "Delete failed: ${e.message}"
                    println(msg)
                    err = e // Store the exception
                }
            }
        }
        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error during delete element")
    }

    /**
     * Finds usages of a PSI element identified by its name and optionally its line number.
     *
     * @param filePath The absolute path to the file containing the element definition.
     * @param codeToSymbol The code from the start of the file up to the symbol name.
     * @return A list of UsageInfo objects representing the found usages, or an empty list if none are found or an error occurs.
     */
    fun findUsage(filePath: String, codeToSymbol: String): Result<List<UsageInfo>, String> {
        // Use ReadAction as we are only reading information, not writing
        return ReadAction.compute<Result<List<UsageInfo>, String>, Throwable> {
            val psiFile = findPsiFile(filePath) ?: run {
                println("Error: Could not find PsiFile for path: $filePath")
                return@compute Err("Could not find PsiFile for path: $filePath")
            }

            val offset = codeToSymbol.length
            val element = findElementAt(psiFile, offset) ?: run {
                println("Error: Could not find element at offset $offset in file: $filePath")
                return@compute Err("Could not find element at offset $offset in file: $filePath")
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

            Ok(usageInfos)
        } // End ReadAction.compute
    }

    /**
     * Moves a file to a target directory.
     *
     * @param targetFilePath The absolute path to the file to move.
     * @param destDirectoryPath The absolute path to the destination directory.
     * @return True if the move operation was successful, false otherwise.
     */
    fun moveFile(targetFilePath: String, destDirectoryPath: String): Result<Unit, String> {
        var err: Exception? = null
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

                    // refactoringCompleted が呼ばれなかったらこのエラーが返される
                    err = Exception("Move operation failed: Target is not a directory.")
                    MoveFilesOrDirectoriesUtil.doMove(
                        project,
                        arrayOf(psiFile),
                        arrayOf(targetDirectory),
                        // MoveCallback is interface. Use a flag as callback might not run if exception occurs before.
                        object : MoveCallback {
                            override fun refactoringCompleted() {
                                err = null // Reset error if callback runs successfully
                                println("Move successful for file $targetFilePath.")
                            }
                            // Consider adding override fun checkConflicts(project: Project, elements: Array<out PsiElement>, targetContainer: PsiElement): Boolean { return false } if needed
                        }
                    )
                    // Note: doMove might not throw exception immediately, success depends on callback
                } catch (e: Exception) {
                    val msg = "Move file failed: ${e.message}"
                    println(msg)
                    err = e // Store the exception
                }
            } // End WriteCommandAction
        } // End invokeAndWait

        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error during move file")
    }

    /**
     * Renames a file.
     *
     * @param targetFilePath The absolute path to the file to rename.
     * @param newName The new name for the file (including extension).
     * @return Result indicating success (Ok) or failure (Err) with an error message.
     */
    fun renameFile(targetFilePath: String, newName: String): Result<Unit, String> {
        var err: Exception? = null
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(targetFilePath) ?: run {
                    val msg = "Error: Could not find PsiFile for path: $targetFilePath"
                    println(msg)
                    err = IllegalStateException(msg)
                    return@runWriteCommandAction
                }

                // Check if an error occurred before proceeding
                if (err != null) return@runWriteCommandAction

                try {
                    println("Attempting to rename file '$targetFilePath' to '$newName'")
                    // Use RefactoringFactory to rename the PsiFile itself
                    val refactoring = RefactoringFactory.getInstance(project).createRename(psiFile, newName)
                    refactoring.doRefactoring(refactoring.findUsages())
                    println("Rename successful for file $targetFilePath.")
                } catch (e: Exception) {
                    val msg = "Rename file failed: ${e.message}"
                    println(msg)
                    err = e // Store the exception
                }
            }
        }
        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error during rename file")
    }

    /**
     * Deletes a file identified by its path.
     *
     * @param filePath The absolute path to the file to delete.
     * @return Result indicating success (Ok) or failure (Err) with an error message.
     */
    fun deleteFile(filePath: String): Result<Unit, String> {
        var err: Exception? = null
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    val msg = "Error: Could not find PsiFile for path: $filePath"
                    println(msg)
                    err = IllegalStateException(msg)
                    return@runWriteCommandAction
                }

                // Check if an error occurred before proceeding
                if (err != null) return@runWriteCommandAction

                try {
                    println("Attempting safe delete for file '$filePath'")
                    // Use SafeDelete refactoring instead of direct deletion
                    val refactoring = RefactoringFactory.getInstance(project).createSafeDelete(arrayOf(psiFile))
                    // Execute the refactoring. This might involve UI or background processing depending on the IDE's implementation.
                    // Using run() is typical for non-interactive execution if supported.
                    // If run() doesn't work or requires UI, doRefactoring might be an alternative,
                    // but safe delete usually handles its own usage checks.
                    refactoring.run() // Execute the safe delete refactoring
                    // Note: Success/failure might be asynchronous or depend on user interaction if prompted.
                    // We'll assume success if no immediate exception is thrown by run().
                    println("Safe delete initiated for file $filePath.")
                } catch (e: Exception) {
                    val msg = "Safe delete failed: ${e.message}" // Updated message
                    println(msg)
                    err = e // Store the exception
                }
            }
        }
        return if (err == null) Ok(Unit) else Err(err!!.message ?: "Unknown error during delete file")
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
