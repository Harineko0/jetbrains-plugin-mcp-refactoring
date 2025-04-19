package com.github.harineko0.jetbrainspluginmcprefactoring.mcp

import com.github.harineko0.jetbrainspluginmcprefactoring.services.CallRefactorService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.modelcontextprotocol.kotlin.sdk.* // Use the correct SDK package
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.* // Import necessary Json elements

class McpServer(private val project: Project) { // Port is likely not needed here anymore

    private val callRefactorService = project.service<CallRefactorService>()

    // Hold the configured server instance
    val server: Server = configureServer()

    private fun configureServer(): Server {
        thisLogger().info("Configuring MCP Server...")
        val serverInstance = Server(
            Implementation(
                name = "jetbrains-refactor-server",
                version = "0.1.1", // Increment version
//                description = "Provides refactoring tools (rename, move, delete) for JetBrains IDEs via MCP."
            ),
            ServerOptions(
                // Enable tool capabilities, assuming listChanged might be useful if tools could be added/removed dynamically
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
            )
        )

        // --- Add Rename Tool ---
        serverInstance.addTool(
            name = "rename_element",
            description = "Renames an element (variable, function, class, etc.) identified by its position.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file.")
                    }
                    putJsonObject("codeToSymbol") { // Changed from symbolName/lineNumber
                        put("type", "string")
                        put("description", "The code from the start of the file up to the symbol to rename.")
                    }
                    putJsonObject("newName") {
                        put("type", "string")
                        put("description", "The new name for the element.")
                    }
                },
                required = listOf("filePath", "codeToSymbol", "newName") // Updated required list
            )
        ) { request ->
            handleRename(request)
        }

        // --- Add Move Tool ---
        serverInstance.addTool(
            name = "move_element",
            description = "Moves an element (likely a file or class) identified by its position to a different directory.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file containing the element to move.")
                    }
                    putJsonObject("codeToSymbol") { // Changed from offset
                        put("type", "string")
                        put("description", "The code from the start of the file up to the symbol to move.")
                    }
                    putJsonObject("targetDirectoryPath") {
                        put("type", "string")
                        put("description", "Absolute path to the target directory.")
                    }
                },
                required = listOf("filePath", "codeToSymbol", "targetDirectoryPath") // Updated required list
            )
        ) { request ->
            handleMove(request)
        }

        // --- Add Delete Tool ---
        serverInstance.addTool(
            name = "delete_element",
            description = "Deletes an element (variable, function, class, file, etc.) identified by its position.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file containing the element.")
                    }
                    putJsonObject("codeToSymbol") { // Changed from offset
                        put("type", "string")
                        put("description", "The code from the start of the file up to the symbol to delete.")
                    }
                },
                required = listOf("filePath", "codeToSymbol") // Updated required list
            )
        ) { request ->
            handleDelete(request)
        }

        // --- Add Find Usages Tool ---
        serverInstance.addTool(
            name = "find_usages", // Consistent naming convention
            description = "Finds all usages of an element (symbol) identified by its position.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file where the element is defined.")
                    }
                    putJsonObject("codeToSymbol") {
                        put("type", "string")
                        put("description", "The code from the start of the file up to the symbol to find usages for.")
                    }
                },
                required = listOf("filePath", "codeToSymbol") // Updated required list
            )
        ) { request ->
            handleFindUsages(request)
        }

        // --- Add Move File Tool ---
        serverInstance.addTool(
            name = "move_file",
            description = "Moves a file to a different directory.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("targetFilePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file to move.")
                    }
                    putJsonObject("destDirectoryPath") {
                        put("type", "string")
                        put("description", "Absolute path to the destination directory.")
                    }
                },
                required = listOf("targetFilePath", "destDirectoryPath")
            )
        ) { request ->
            handleMoveFile(request)
        }

        // --- Add Rename File Tool ---
        serverInstance.addTool(
            name = "rename_file",
            description = "Renames a file.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("targetFilePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file to rename.")
                    }
                    putJsonObject("newName") {
                        put("type", "string")
                        put("description", "The new name for the file (including extension).")
                    }
                },
                required = listOf("targetFilePath", "newName")
            )
        ) { request ->
            handleRenameFile(request)
        }


        thisLogger().info("MCP Server configured with tools.")
        return serverInstance
    }


    // --- Tool Handlers ---

    private fun handleRename(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val codeToSymbol = args["codeToSymbol"]?.jsonPrimitive?.contentOrNull // Get codeToSymbol
            val newName = args["newName"]?.jsonPrimitive?.contentOrNull

            // Updated validation
            if (filePath == null || codeToSymbol == null || newName == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, codeToSymbol, newName).")))
            }

            // Updated logging
            thisLogger().info("Handling rename: filePath=$filePath, codeToSymbol (length=${codeToSymbol.length}), newName=$newName")
            // Updated service call
            val result = callRefactorService.renameElement(filePath, codeToSymbol, newName)

            if (result.isOk) {
                // Use codeToSymbol in message for context, maybe truncate if too long
                val context = if (codeToSymbol.length > 50) codeToSymbol.takeLast(50) + "..." else codeToSymbol
                showNotification(
                    "Element near '$context' renamed successfully to '$newName'.",
                    NotificationType.INFORMATION
                )
                CallToolResult(content = listOf(TextContent("Element near '$context' renamed successfully to '$newName'.")))
            } else {
                showNotification("Error: Rename operation failed. ${result.error}", NotificationType.ERROR)
                CallToolResult(content = listOf(TextContent("Error: Rename operation failed. ${result.error}")))
            }
        } catch (e: Exception) {
            showNotification("Error: Failed to process rename request: ${e.message}", NotificationType.ERROR)
            CallToolResult(content = listOf(TextContent("Error: Failed to process rename request: ${e.message}")))
        }
    }

    private fun handleMove(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val codeToSymbol = args["codeToSymbol"]?.jsonPrimitive?.contentOrNull // Get codeToSymbol
            val targetDirectoryPath = args["targetDirectoryPath"]?.jsonPrimitive?.contentOrNull

            // Updated validation
            if (filePath == null || codeToSymbol == null || targetDirectoryPath == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, codeToSymbol, targetDirectoryPath).")))
            }

            // Updated logging
            thisLogger().info("Handling move: filePath=$filePath, codeToSymbol (length=${codeToSymbol.length}), targetDir=$targetDirectoryPath")
            // Updated service call
            val result = callRefactorService.moveElement(filePath, codeToSymbol, targetDirectoryPath)

            if (result.isOk) {
                showNotification("Element moved successfully to '$targetDirectoryPath'.", NotificationType.INFORMATION)
                CallToolResult(content = listOf(TextContent("Element moved successfully to '$targetDirectoryPath'.")))
            } else {
                showNotification("Error: Move operation failed. Check IDE logs.", NotificationType.ERROR)
                CallToolResult(content = listOf(TextContent("Error: Move operation failed. Check IDE logs.")))
            }
        } catch (e: Exception) {
            showNotification("Error: Failed to process move request: ${e.message}", NotificationType.ERROR)
            CallToolResult(content = listOf(TextContent("Error: Failed to process move request: ${e.message}")))
        }
    }

    private fun handleDelete(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val codeToSymbol = args["codeToSymbol"]?.jsonPrimitive?.contentOrNull // Get codeToSymbol

            // Updated validation
            if (filePath == null || codeToSymbol == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, codeToSymbol).")))
            }

            // Updated logging
            thisLogger().info("Handling delete: filePath=$filePath, codeToSymbol (length=${codeToSymbol.length})")
            // Updated service call
            val result = callRefactorService.deleteElement(filePath, codeToSymbol)

            if (result.isOk) {
                showNotification("Element deleted successfully.", NotificationType.INFORMATION)
                CallToolResult(content = listOf(TextContent("Element deleted successfully.")))
            } else {
                showNotification("Error: Delete operation failed. Check IDE logs.", NotificationType.ERROR)
                CallToolResult(content = listOf(TextContent("Error: Delete operation failed. Check IDE logs.")))
            }
        } catch (e: Exception) {
            showNotification("Error: Failed to process delete request: ${e.message}", NotificationType.ERROR)
            CallToolResult(content = listOf(TextContent("Error: Failed to process delete request: ${e.message}")))
        }
    }

    private fun handleFindUsages(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val codeToSymbol = args["codeToSymbol"]?.jsonPrimitive?.contentOrNull

            // Validation (already correct)
            if (filePath == null || codeToSymbol == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, codeToSymbol).")))
            }

            // Logging (already correct)
            thisLogger().info("Handling find usages: filePath=$filePath, codeToSymbol (length=${codeToSymbol.length})")
            // Service call (already correct)
            val result = callRefactorService.findUsage(filePath, codeToSymbol)

            if (result.isOk) {
                val usages = result.value
                // Format the usages into a readable string or structured JSON
                val formattedUsages = usages.joinToString("\n") { usage ->
                    "- ${usage.filePath}:${usage.lineNumber}:${usage.columnNumber} : ${usage.usageTextSnippet}"
                }
                // Use a more generic message as codeToSymbol might be long
                val resultText = "Found ${usages.size} usage(s):\n$formattedUsages"
                showNotification(resultText, NotificationType.INFORMATION)
                CallToolResult(content = listOf(TextContent(resultText)))
            } else {
                CallToolResult(content = listOf(TextContent("No usages found.")))
            }
        } catch (e: Exception) {
            showNotification("Error: Failed to process find usages request: ${e.message}", NotificationType.ERROR)
            thisLogger().error("Error processing find usages request: ${e.message}", e)
            CallToolResult(content = listOf(TextContent("Error: Failed to process find usages request: ${e.message}")))
        }
    }

    private fun handleMoveFile(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val targetFilePath = args["targetFilePath"]?.jsonPrimitive?.contentOrNull
            val destDirectoryPath = args["destDirectoryPath"]?.jsonPrimitive?.contentOrNull

            if (targetFilePath == null || destDirectoryPath == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (targetFilePath, destDirectoryPath).")))
            }

            thisLogger().info("Handling move file: targetFilePath=$targetFilePath, destDirectoryPath=$destDirectoryPath")
            val result = callRefactorService.moveFile(targetFilePath, destDirectoryPath)

            if (result.isOk) {
                CallToolResult(content = listOf(TextContent("File '$targetFilePath' moved successfully to '$destDirectoryPath'.")))
            } else {
                CallToolResult(content = listOf(TextContent("Error: Move file operation failed. Check IDE logs.")))
            }
        } catch (e: Exception) {
            thisLogger().error("Error processing move file request: ${e.message}", e)
            showNotification("Error: Failed to process move file request: ${e.message}", NotificationType.ERROR)
            CallToolResult(content = listOf(TextContent("Error: Failed to process move file request: ${e.message}")))
        }
    }

    private fun handleRenameFile(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val targetFilePath = args["targetFilePath"]?.jsonPrimitive?.contentOrNull
            val newName = args["newName"]?.jsonPrimitive?.contentOrNull

            if (targetFilePath == null || newName == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (targetFilePath, newName).")))
            }

            thisLogger().info("Handling rename file: targetFilePath=$targetFilePath, newName=$newName")
            val result = callRefactorService.renameFile(targetFilePath, newName)

            if (result.isOk) {
                val successMsg = "File '$targetFilePath' renamed successfully to '$newName'."
                showNotification(successMsg, NotificationType.INFORMATION)
                CallToolResult(content = listOf(TextContent(successMsg)))
            } else {
                val errorMsg = "Error: Rename file operation failed. ${result.error}"
                showNotification(errorMsg, NotificationType.ERROR)
                CallToolResult(content = listOf(TextContent(errorMsg)))
            }
        } catch (e: Exception) {
            showNotification("Error: Failed to process rename file request: ${e.message}", NotificationType.ERROR)
            thisLogger().error("Error processing rename file request: ${e.message}", e)
            CallToolResult(content = listOf(TextContent("Error: Failed to process rename file request: ${e.message}")))
        }
    }

    private fun showNotification(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("MCP Server Notifications") // Use the same ID defined above or a known one
            .createNotification(content, type)
            .notify(project)
    }
}
