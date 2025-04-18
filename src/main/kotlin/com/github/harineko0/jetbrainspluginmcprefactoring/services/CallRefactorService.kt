package com.github.harineko0.jetbrainspluginmcprefactoring.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class CallRefactorService(private val project: Project) {

    /**
     * Renames a PSI element at the specified offset in a file.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param offset The offset of the element within the file.
     * @param newName The new name for the element.
     * @return True if the rename operation was successful, false otherwise.
     */
    fun renameElement(filePath: String, offset: Int, newName: String): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction // Use qualified return
                }
                val element = findElementAt(psiFile, offset) ?: run {
                    println("Error: Could not find element at offset $offset in file: $filePath")
                    return@runWriteCommandAction // Use qualified return
                }

                try {
                    println("Attempting to rename element: ${element.text} to $newName")
                    val renameRefactoring = RefactoringFactory.getInstance(project).createRename(element, newName)
                    // Consider adding options like searchInComments or searchInNonJavaFiles if needed
                    // renameRefactoring.setSearchInComments(true)
                    // renameRefactoring.setSearchInNonJavaFiles(true)
                    renameRefactoring.run()
                    println("Rename successful.")
                    success = true
                } catch (e: Exception) {
                    // Log the exception for better debugging
                    println("Rename failed: ${e.message}")
                    e.printStackTrace() // Print stack trace for detailed error info
                }
            }
        }
        return success
    }

    /**
     * Moves a PSI element at the specified offset in a file to a target directory.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param offset The offset of the element within the file.
     * @param targetDirectoryPath The absolute path to the target directory.
     * @return True if the move operation was successful, false otherwise.
     */
    fun moveElement(filePath: String, offset: Int, targetDirectoryPath: String): Boolean {
        // TODO: Implement Move refactoring using JetBrains Platform SDK
        // 1. Find the PsiElement to move (similar to renameElement).
        // 2. Find the target PsiDirectory using targetDirectoryPath.
        // 3. Use RefactoringFactory.getInstance(project).createMove(...)
        // 4. Configure move options if necessary.
        // 5. Run the refactoring within invokeAndWait and WriteCommandAction.
        // 6. Handle potential exceptions.
        println("Move element: filePath=$filePath, offset=$offset, targetDirectoryPath=$targetDirectoryPath")
        // Placeholder implementation
        return false // Return false until implemented
    }

    /**
     * Deletes a PSI element at the specified offset in a file.
     *
     * @param filePath The absolute path to the file containing the element.
     * @param offset The offset of the element within the file.
     * @return True if the delete operation was successful, false otherwise.
     */
    fun deleteElement(filePath: String, offset: Int): Boolean {
        // TODO: Implement Delete refactoring using JetBrains Platform SDK
        // 1. Find the PsiElement to delete (similar to renameElement).
        // 2. Use SafeDeleteHandler.invoke(project, arrayOf(element), true) or similar API.
        // 3. Run the refactoring within invokeAndWait and WriteCommandAction.
        // 4. Handle potential exceptions.
        println("Delete element: filePath=$filePath, offset=$offset")
        // Placeholder implementation
        return false // Return false until implemented
    }

    fun findUsage(filePath: String, offset: Int): Collection<PsiReference> {
        var usages: Collection<PsiReference>? = null
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction // Use qualified return
                }
                val element = findElementAt(psiFile, offset) ?: run {
                    println("Error: Could not find element at offset $offset in file: $filePath")
                    return@runWriteCommandAction // Use qualified return
                }

                try {
                    println("Searching usages of ${element.text}")
                    usages = ReferencesSearch.search(element).findAll();
                    println("Search successful.")
                } catch (e: Exception) {
                    // Log the exception for better debugging
                    println("Rename failed: ${e.message}")
                    e.printStackTrace() // Print stack trace for detailed error info
                }
            }
        }
        return usages ?: emptyList()
    }

    /**
     * Finds the PsiFile corresponding to the given file path.
     * Requires read access, should be called within invokeAndWait or ReadAction.
     */
    private fun findPsiFile(filePath: String): PsiFile? {
        // Ensure we are in a read action to safely access VFS and PSI
        var psiFile: PsiFile? = null
        ApplicationManager.getApplication().runReadAction {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath))
            psiFile = virtualFile?.let {
                if (it.isValid) {
                    PsiManager.getInstance(project).findFile(it)
                } else {
                    println("Warning: VirtualFile is invalid for path: $filePath")
                    null
                }
            }
        }
        return psiFile
    }

    /**
     * Finds the PsiElement at the specified offset within a PsiFile.
     * Requires read access, should be called within invokeAndWait or ReadAction.
     */
    private fun findElementAt(psiFile: PsiFile, offset: Int): PsiElement? {
        // Ensure we are in a read action to safely access PSI
        var element: PsiElement? = null
        ApplicationManager.getApplication().runReadAction {
             element = psiFile.findElementAt(offset)
             if (element == null) {
                 println("Warning: No element found at offset $offset in file ${psiFile.virtualFile?.path}")
             }
        }
       return element
    }
}
