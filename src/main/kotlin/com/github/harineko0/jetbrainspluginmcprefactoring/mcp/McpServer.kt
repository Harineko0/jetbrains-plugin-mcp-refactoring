package com.github.harineko0.jetbrainspluginmcprefactoring.mcp

import com.github.harineko0.jetbrainspluginmcprefactoring.services.CallRefactorService
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
            description = "Renames an element (variable, function, class, etc.) identified by its name and optionally line number.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file.")
                    }
                    putJsonObject("symbolName") { // Changed from offset
                        put("type", "string")
                        put("description", "The name of the element (symbol) to rename.")
                    }
                    putJsonObject("lineNumber") { // Added lineNumber
                        put("type", "integer")
                        put("description", "Optional: The approximate line number where the symbol is located to help disambiguate.")
                    }
                    putJsonObject("newName") {
                        put("type", "string")
                        put("description", "The new name for the element.")
                    }
                },
                required = listOf("filePath", "symbolName", "newName") // Updated required list
            )
        ) { request ->
            handleRename(request) // Handler remains the same, logic inside changes
        }

        // --- Add Move Tool ---
        serverInstance.addTool(
            name = "move_element",
            description = "Moves an element (likely a file or class) to a different directory.",
            inputSchema = Tool.Input(
                 properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file containing the element to move.")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Zero-based offset of the element to move (often the start of the file/class definition).")
                    }
                    putJsonObject("targetDirectoryPath") {
                        put("type", "string")
                        put("description", "Absolute path to the target directory.")
                    }
                },
                required = listOf("filePath", "offset", "targetDirectoryPath")
            )
        ) { request ->
            handleMove(request)
        }

        // --- Add Delete Tool ---
        serverInstance.addTool(
            name = "delete_element",
            description = "Deletes an element (variable, function, class, file, etc.) at a specific location.",
            inputSchema = Tool.Input(
                 properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file containing the element.")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Zero-based offset of the element to delete within the file.")
                    }
                },
                required = listOf("filePath", "offset")
            )
        ) { request ->
            handleDelete(request)
        }

        // --- Add Find Usages Tool ---
        serverInstance.addTool(
            name = "find_usages", // Consistent naming convention
            description = "Finds all usages of an element (symbol) identified by its name and optionally line number.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("filePath") {
                        put("type", "string")
                        put("description", "Absolute path to the file where the element is defined.")
                    }
                    putJsonObject("codeToSymbol") { // Changed from symbolName
                        put("type", "string")
                        put("description", "The code of <filePath> from top to position of the symbol.")
                    }
                },
                required = listOf("filePath", "symbolName") // Line number is optional
            )
        ) { request ->
            handleFindUsages(request) // New handler function
        }


        thisLogger().info("MCP Server configured with tools.")
        return serverInstance
    }


    // --- Tool Handlers ---

    private fun handleRename(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val symbolName = args["symbolName"]?.jsonPrimitive?.contentOrNull // Get symbolName
            val lineNumber = args["lineNumber"]?.jsonPrimitive?.intOrNull // Get optional lineNumber
            val newName = args["newName"]?.jsonPrimitive?.contentOrNull

            // Updated validation
            if (filePath == null || symbolName == null || newName == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, symbolName, newName).")))
            }

            // Updated logging
            thisLogger().info("Handling rename: filePath=$filePath, symbolName=$symbolName, lineNumber=$lineNumber, newName=$newName")
            // Updated service call
            val success = callRefactorService.renameElement(project, filePath, symbolName, lineNumber, newName) // Pass project and new args

            if (success) {
                CallToolResult(content = listOf(TextContent("Element '$symbolName' renamed successfully to '$newName'.")))
            } else {
                CallToolResult(content = listOf(TextContent("Error: Rename operation failed. Check IDE logs for details.")))
            }
        } catch (e: Exception) {
            thisLogger().error("Error processing rename request: ${e.message}", e)
            CallToolResult(content = listOf(TextContent("Error: Failed to process rename request: ${e.message}")))
        }
    }

     private fun handleMove(request: CallToolRequest): CallToolResult {
         return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val offset = args["offset"]?.jsonPrimitive?.intOrNull
            val targetDirectoryPath = args["targetDirectoryPath"]?.jsonPrimitive?.contentOrNull

             if (filePath == null || offset == null || targetDirectoryPath == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, offset, targetDirectoryPath).")))
            }

            thisLogger().info("Handling move: filePath=$filePath, offset=$offset, targetDir=$targetDirectoryPath")
            // TODO: Implement the actual move logic call in CallRefactorService
            val success = callRefactorService.moveElement(filePath, offset, targetDirectoryPath)

            if (success) {
                 CallToolResult(content = listOf(TextContent("Element moved successfully to '$targetDirectoryPath'.")))
            } else {
                 // Provide more specific feedback once implemented
                 CallToolResult(content = listOf(TextContent("Error: Move operation failed (or not yet implemented). Check IDE logs.")))
            }
         } catch (e: Exception) {
             thisLogger().error("Error processing move request: ${e.message}", e)
             CallToolResult(content = listOf(TextContent("Error: Failed to process move request: ${e.message}")))
         }
    }

    private fun handleDelete(request: CallToolRequest): CallToolResult {
         return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val offset = args["offset"]?.jsonPrimitive?.intOrNull

             if (filePath == null || offset == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, offset).")))
            }

            thisLogger().info("Handling delete: filePath=$filePath, offset=$offset")
            // TODO: Implement the actual delete logic call in CallRefactorService
            val success = callRefactorService.deleteElement(filePath, offset)

            if (success) {
                 CallToolResult(content = listOf(TextContent("Element deleted successfully.")))
            } else {
                 // Provide more specific feedback once implemented
                 CallToolResult(content = listOf(TextContent("Error: Delete operation failed (or not yet implemented). Check IDE logs.")))
            }
         } catch (e: Exception) {
             thisLogger().error("Error processing delete request: ${e.message}", e)
             CallToolResult(content = listOf(TextContent("Error: Failed to process delete request: ${e.message}")))
         }
    }

    private fun handleFindUsages(request: CallToolRequest): CallToolResult {
        return try {
            val args = request.arguments
            val filePath = args["filePath"]?.jsonPrimitive?.contentOrNull
            val codeToSymbol = args["codeToSymbol"]?.jsonPrimitive?.contentOrNull

            if (filePath == null || codeToSymbol == null) {
                return CallToolResult(content = listOf(TextContent("Error: Missing required arguments (filePath, symbolName).")))
            }

            thisLogger().info("Handling find usages: filePath=$filePath, symbolName=$codeToSymbol")
            val usages = callRefactorService.findUsage(filePath, codeToSymbol)

            if (usages.isNotEmpty()) {
                // Format the usages into a readable string or structured JSON
                val formattedUsages = usages.joinToString("\n") { usage ->
                    "- ${usage.filePath}:${usage.lineNumber}:${usage.columnNumber} : ${usage.usageTextSnippet}"
                }
                val resultText = "Found ${usages.size} usage(s) for '$codeToSymbol':\n$formattedUsages"
                CallToolResult(content = listOf(TextContent(resultText)))
                // Alternatively, return structured JSON:
                // val jsonUsages = buildJsonArray {
                //     usages.forEach { usage ->
                //         addJsonObject {
                //             put("filePath", usage.filePath)
                //             put("lineNumber", usage.lineNumber)
                //             put("columnNumber", usage.columnNumber)
                //             put("snippet", usage.usageTextSnippet)
                //         }
                //     }
                // }
                // CallToolResult(content = listOf(JsonContent(jsonUsages)))
            } else {
                CallToolResult(content = listOf(TextContent("No usages found for '$codeToSymbol'.")))
            }
        } catch (e: Exception) {
            thisLogger().error("Error processing find usages request: ${e.message}", e)
            CallToolResult(content = listOf(TextContent("Error: Failed to process find usages request: ${e.message}")))
        }
    }


    // Note: The start/stop logic is removed from here.
    // The lifecycle (creation/disposal) will be managed by MyToolWindowFactory.
    // The server doesn't need an explicit start/connect call in this context,
    // as it's not using a standard transport like Stdio or SSE.
    // The tools are registered during configuration.
}
