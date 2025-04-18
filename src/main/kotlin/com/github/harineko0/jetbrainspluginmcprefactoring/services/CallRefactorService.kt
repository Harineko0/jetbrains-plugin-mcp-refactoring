package com.github.harineko0.jetbrainspluginmcprefactoring.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringFactory
import java.nio.file.Paths
import kotlin.math.abs

@Service(Service.Level.PROJECT)
class CallRefactorService(private val project: Project) {

    /**
     * Renames a PSI element identified by its name and optionally its line number.
     *
     * @param project The current project.
     * @param filePath The absolute path to the file containing the element.
     * @param symbolName The name of the symbol (element) to rename.
     * @param lineNumber Optional: The approximate line number to help locate the symbol.
     * @param newName The new name for the element.
     * @return True if the rename operation was successful, false otherwise.
     */
    fun renameElement(
        project: Project,
        filePath: String,
        symbolName: String,
        lineNumber: Int?,
        newName: String
    ): Boolean {
        var success = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = findPsiFile(filePath) ?: run {
                    println("Error: Could not find PsiFile for path: $filePath")
                    return@runWriteCommandAction
                }

                // Find the element using the new logic
                val element = findElementByNameAndLine(psiFile, symbolName, lineNumber) ?: run {
                    println("Error: Could not find unique element with name '$symbolName' ${if (lineNumber != null) "near line $lineNumber" else ""} in file: $filePath")
                    return@runWriteCommandAction
                }

                try {
                    println("Attempting to rename element '${element.text}' at offset ${element.textOffset} to '$newName'")
                    val renameRefactoring = RefactoringFactory.getInstance(project).createRename(element, newName)
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
        return ReadAction.compute<PsiElement?, Throwable> {
            val element = psiFile.findElementAt(offset)
            if (element == null) {
                println("Warning: No element found at offset $offset in file ${psiFile.virtualFile?.path}")
            }
            // Often findElementAt returns whitespace or punctuation. Try to get the parent identifier/named element.
            element?.parentOfType<PsiNameIdentifierOwner>(withSelf = true) ?: element
        }
    }


    /**
     * Finds a PsiElement within a PsiFile based on its name and optionally its line number.
     * Tries to find the most relevant element matching the criteria.
     * Requires read access.
     */
    private fun findElementByNameAndLine(psiFile: PsiFile, name: String, targetLine: Int?): PsiElement? {
        return ReadAction.compute<PsiElement?, Throwable> {
            val candidates = mutableListOf<PsiElement>()
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: run {
                println("Error: Could not get document for ${psiFile.name}")
                return@compute null
            }

            // Visitor to find elements with the matching name
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    // Check if the element is a named element and its name matches
                    // Using PsiNameIdentifierOwner is a common way to find named declarations
                    if (element is PsiNameIdentifierOwner) {
                        if (element.name == name) {
                            // Add the identifier element itself if it exists, otherwise the owner
                            candidates.add(element.nameIdentifier ?: element)
                        }
                    } else if (element.text == name) {
                        // Fallback for elements that might not be PsiNameIdentifierOwner but have matching text
                        // Be cautious with this, might match comments or string literals
                        // Let's prioritize named elements, maybe add this later if needed
                    }
                }
            })

            if (candidates.isEmpty()) {
                println("No elements found with name '$name' in ${psiFile.name}")
                return@compute null
            }

            if (candidates.size == 1) {
                println("Found unique element with name '$name' at offset ${candidates.first().textOffset}")
                return@compute candidates.first()
            }

            // Multiple candidates, use line number to disambiguate if provided
            if (targetLine != null) {
                println("Multiple elements found with name '$name'. Using line number $targetLine to disambiguate.")
                var bestCandidate: PsiElement? = null
                var minLineDiff = Int.MAX_VALUE

                for (candidate in candidates) {
                    val line = document.getLineNumber(candidate.textOffset) + 1 // Document lines are 0-based
                    val diff = abs(line - targetLine)
                    println("  - Candidate at line $line (offset ${candidate.textOffset}), diff: $diff")
                    if (diff < minLineDiff) {
                        minLineDiff = diff
                        bestCandidate = candidate
                    }
                }
                // Optional: Add a threshold for max allowed line difference?
                if (bestCandidate != null) {
                    println(
                        "Selected candidate at offset ${bestCandidate.textOffset} (line ${
                            document.getLineNumber(
                                bestCandidate.textOffset
                            ) + 1
                        }) as closest to target line $targetLine."
                    )
                } else {
                    println("Could not select a best candidate based on line number.")
                }
                return@compute bestCandidate // Return the closest one found
            } else {
                // Multiple candidates, no line number provided
                println("Error: Multiple elements found with name '$name' but no line number provided for disambiguation.")
                candidates.forEachIndexed { index, cand ->
                    val line = document.getLineNumber(cand.textOffset) + 1
                    println("  [$index] Offset: ${cand.textOffset}, Line: $line, Text: ${cand.text}")
                }
                return@compute null // Cannot determine unique element
            }
        }
    }

    // Helper extension function to get parent of a specific type
    inline fun <reified T : PsiElement> PsiElement.parentOfType(withSelf: Boolean): T? {
        return PsiTreeUtil.getParentOfType(this, T::class.java, !withSelf)
    }
}
