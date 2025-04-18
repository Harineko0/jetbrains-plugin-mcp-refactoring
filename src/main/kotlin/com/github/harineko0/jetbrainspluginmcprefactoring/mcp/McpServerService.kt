package com.github.harineko0.jetbrainspluginmcprefactoring.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.usageView.UsageInfo
// MCP SDK Imports - Updated based on README
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
// ---
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

// --- Input Data Classes for MCP Tools ---

data class RenameInput(
    val filePath: String,
    val offset: Int, // Character offset in the file
    val newName: String
)

data class MoveInput(
    val filePath: String,
    val newPath: String // Target directory path
)

data class DeleteInput(
    val filePath: String
)

// --- Service Implementation ---

@Service(Service.Level.PROJECT)
class McpServerService(private val project: Project, private val scope: CoroutineScope) {

    private val logger = Logger.getLogger(McpServerService::class.java.name)
    private var server: Server? = null // Renamed from mcpServer
    private val gson = Gson()

    init {
        logger.info("Initializing McpServerService for project: ${project.name}")
        startServer()
    }

    private fun startServer() {
        scope.launch(Dispatchers.IO) {
            try {
                // Define server info and capabilities
                val serverInfo = Implementation(
                    name = "jetbrains-refactor-server",
                    version = "0.1.0" // TODO: Get version dynamically?
                )
                val capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true) // Allow dynamic tool registration
                )
                val options = ServerOptions(
                    capabilities = capabilities
                )

                server = Server(serverInfo = serverInfo, options = options) // Use updated classes

                // Define and register MCP tools for refactoring
                registerRefactoringTools()

                server?
                val actualPort = server?.getPort()
                logger.info("MCP Server started for project ${project.name} on port $actualPort")

                // TODO: How to communicate the port back or make it discoverable?
                // Maybe write to a file, log prominently, or use a notification?

            } catch (e: Exception) {
                logger.severe("Failed to start MCP server for project ${project.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun registerRefactoringTools() {
        val currentServer = server ?: return logger.severe("MCP Server not initialized, cannot register tools.") // Use renamed server variable

        // --- Rename Tool ---
        currentServer.defineTool( // Use renamed server variable
            ToolDefinition(
                name = "refactor_rename",
                description = "Renames an element at a specific file offset.", // Made description generic
                inputSchema = gson.toJsonTree(RenameInput::class.java).asJsonObject // Basic schema from data class
            )
        ) { params: JsonObject, _: Connection -> handleRename(params) } // Updated Connection type

        // --- Move Tool ---
        currentServer.defineTool( // Use renamed server variable
            ToolDefinition(
                name = "refactor_move",
                description = "Moves a file or directory.",
                inputSchema = gson.toJsonTree(MoveInput::class.java).asJsonObject
            )
        ) { params: JsonObject, _: Connection -> handleMove(params) } // Updated Connection type

        // --- Delete Tool ---
        currentServer.defineTool( // Use renamed server variable
            ToolDefinition(
                name = "refactor_delete",
                description = "Safely deletes a file or directory.",
                inputSchema = gson.toJsonTree(DeleteInput::class.java).asJsonObject
            )
        ) { params: JsonObject, _: Connection -> handleDelete(params) } // Updated Connection type

        logger.info("Registered MCP refactoring tools.")
    }

    // --- Tool Handlers (Placeholders - Need Implementation) ---

    private suspend fun handleRename(params: JsonObject): ToolResult {
        return try {
            val input = gson.fromJson(params, RenameInput::class.java)
            logger.info("Received rename request: $input")
            var errorMessage: String? = null

            withContext(Dispatchers.EDT) { // Switch to Event Dispatch Thread for UI/PSI actions
                WriteCommandAction.runWriteCommandAction(project, "MCP Rename Refactoring") {
                    try {
                        val virtualFile = findVirtualFile(input.filePath)
                        if (virtualFile == null) {
                            errorMessage = "File not found: ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        val element = findElementAtOffset(virtualFile, input.offset)
                        if (element == null) {
                            errorMessage = "No element found at offset ${input.offset} in ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        // Check if the element can be renamed (basic check)
                        // More robust checks might involve `RenameUtil.canRename` or specific refactoring support checks
                        if (!element.isWritable) {
                             errorMessage = "Element at offset ${input.offset} is not writable."
                             return@runWriteCommandAction
                        }

                        logger.info("Attempting to rename element: ${element.text} (type: ${element.node.elementType}) to '${input.newName}'")

                        // Perform the rename
                        // Using UsageInfo.EMPTY_ARRAY assumes we don't need to preprocess usages
                        RenameUtil.doRename(element, input.newName, UsageInfo.EMPTY_ARRAY, project, null)

                        logger.info("Rename successful for element at offset ${input.offset} in ${input.filePath}")

                    } catch (t: Throwable) {
                        logger.log(Level.SEVERE, "Exception during rename execution: ${t.message}", t)
                        errorMessage = "Rename failed: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }

            if (errorMessage != null) {
                ToolResult(JsonObject().apply {
                    addProperty("status", "error")
                    addProperty("message", errorMessage)
                })
            } else {
                ToolResult(JsonObject().apply { addProperty("status", "success") })
            }
        } catch (e: Exception) { // Catch exceptions outside the WriteCommandAction
            logger.log(Level.SEVERE, "Error processing rename request: ${e.message}", e)
            ToolResult(JsonObject().apply {
                addProperty("status", "error")
                addProperty("message", e.message ?: "Unknown error")
            })
        }
    }

    private suspend fun handleMove(params: JsonObject): ToolResult {
         return try {
            val input = gson.fromJson(params, MoveInput::class.java)
            logger.info("Received move request: $input")
            var errorMessage: String? = null

            withContext(Dispatchers.EDT) {
                WriteCommandAction.runWriteCommandAction(project, "MCP Move Refactoring") {
                    try {
                        val sourceVirtualFile = findVirtualFile(input.filePath)
                        if (sourceVirtualFile == null) {
                            errorMessage = "Source file or directory not found: ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        val targetVirtualDir = findVirtualFile(input.newPath)
                        if (targetVirtualDir == null || !targetVirtualDir.isDirectory) {
                            errorMessage = "Target directory not found or is not a directory: ${input.newPath}"
                            return@runWriteCommandAction
                        }

                        val psiManager = PsiManager.getInstance(project)
                        val elementToMove: PsiElement? = if (sourceVirtualFile.isDirectory) {
                            psiManager.findDirectory(sourceVirtualFile)
                        } else {
                            psiManager.findFile(sourceVirtualFile)
                        }

                        val targetDirectory: PsiDirectory? = psiManager.findDirectory(targetVirtualDir)

                        if (elementToMove == null) {
                            errorMessage = "Could not find source PSI element for: ${input.filePath}"
                            return@runWriteCommandAction
                        }
                        if (targetDirectory == null) {
                            errorMessage = "Could not find target PSI directory for: ${input.newPath}"
                            return@runWriteCommandAction
                        }

                        if (!elementToMove.isWritable || !targetDirectory.isWritable) {
                            errorMessage = "Source or target element is not writable."
                            return@runWriteCommandAction
                        }

                        logger.info("Attempting to move element: ${elementToMove.name} to directory ${targetDirectory.name}")

                        // Perform the move
                        MoveFilesOrDirectoriesUtil.doMove(project, arrayOf(elementToMove), arrayOf(targetDirectory), null, null)

                        logger.info("Move successful for: ${input.filePath} to ${input.newPath}")

                    } catch (t: Throwable) {
                        logger.log(Level.SEVERE, "Exception during move execution: ${t.message}", t)
                        errorMessage = "Move failed: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }

            if (errorMessage != null) {
                ToolResult(JsonObject().apply {
                    addProperty("status", "error")
                    addProperty("message", errorMessage)
                })
            } else {
                ToolResult(JsonObject().apply { addProperty("status", "success") })
            }
        } catch (e: Exception) { // Catch exceptions outside the WriteCommandAction
            logger.log(Level.SEVERE, "Error processing move request: ${e.message}", e)
            ToolResult(JsonObject().apply {
                addProperty("status", "error")
                addProperty("message", e.message ?: "Unknown error")
            })
        }
    }

    // --- Helper Functions ---

    private fun findVirtualFile(filePath: String): VirtualFile? {
        // Ensure the path is absolute and canonical if necessary
        val ioFile = File(filePath).absoluteFile
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
    }

     private fun findElementAtOffset(virtualFile: VirtualFile, offset: Int): PsiElement? {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        // Find the element at the specified offset, regardless of language.
        return psiFile.findElementAt(offset)
    }

     private suspend fun handleDelete(params: JsonObject): ToolResult {
         return try {
            val input = gson.fromJson(params, DeleteInput::class.java)
            logger.info("Received delete request: $input")
            var errorMessage: String? = null

            withContext(Dispatchers.EDT) {
                WriteCommandAction.runWriteCommandAction(project, "MCP Delete Refactoring") {
                    try {
                        val virtualFile = findVirtualFile(input.filePath)
                        if (virtualFile == null) {
                            errorMessage = "File or directory not found: ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        val psiManager = PsiManager.getInstance(project)
                        val elementToDelete: PsiElement? = if (virtualFile.isDirectory) {
                            psiManager.findDirectory(virtualFile)
                        } else {
                            psiManager.findFile(virtualFile)
                        }

                        if (elementToDelete == null) {
                            errorMessage = "Could not find PSI element for: ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        if (!elementToDelete.isWritable) {
                            errorMessage = "Element is not writable: ${input.filePath}"
                            return@runWriteCommandAction
                        }

                        logger.info("Attempting to delete element: ${elementToDelete.name} (type: ${elementToDelete.javaClass.simpleName})")

                        // Perform the safe delete
                        // The `true` parameter typically means "search for usages"
                        // We might want to make this configurable or default to false for non-interactive use.
                        // Let's default to not searching usages for now (false).
                        SafeDeleteHandler.invoke(project, arrayOf(elementToDelete), false) // false = don't search usages

                        logger.info("Safe delete successful for: ${input.filePath}")

                    } catch (t: Throwable) {
                        logger.log(Level.SEVERE, "Exception during delete execution: ${t.message}", t)
                        errorMessage = "Delete failed: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }

            if (errorMessage != null) {
                ToolResult(JsonObject().apply {
                    addProperty("status", "error")
                    addProperty("message", errorMessage)
                })
            } else {
                ToolResult(JsonObject().apply { addProperty("status", "success") })
            }
        } catch (e: Exception) { // Catch exceptions outside the WriteCommandAction
            logger.log(Level.SEVERE, "Error processing delete request: ${e.message}", e)
            ToolResult(JsonObject().apply {
                 addProperty("status", "error")
                 addProperty("message", e.message ?: "Unknown error")
             })
         }
    }


    fun dispose() {
        logger.info("Disposing McpServerService for project: ${project.name}")
        scope.launch(Dispatchers.IO) {
            server?.stop() // Use renamed server variable
            logger.info("MCP Server stopped for project ${project.name}")
        }
        // Cancel the scope to clean up coroutines
        // Note: Using SupervisorJob, so cancelling the parent scope won't affect children started independently,
        // but it's good practice for services managing their own scope.
        // However, the scope is injected by IntelliJ, so we shouldn't cancel it here directly.
        // Instead, rely on IntelliJ's lifecycle management.
        // scope.cancel() // DO NOT DO THIS if scope is injected by IntelliJ
    }

    // Companion object to manage the CoroutineScope for the service
    // This scope will be cancelled when the service is disposed by IntelliJ
    companion object {
        fun getInstance(project: Project): McpServerService = project.getService(McpServerService::class.java)
    }
}

// Note: We need to register this service in plugin.xml
// We also need to ensure the CoroutineScope is properly injected or created.
// The template might already provide a way to inject a project-level scope.
// If not, we might need to create one using `serviceScope` or similar.
// Let's assume for now that IntelliJ injects a suitable scope.
