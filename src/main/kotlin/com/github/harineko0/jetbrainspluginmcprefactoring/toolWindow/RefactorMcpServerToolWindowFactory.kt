package com.github.harineko0.jetbrainspluginmcprefactoring.toolWindow

import com.github.harineko0.jetbrainspluginmcprefactoring.MyBundle
import com.github.harineko0.jetbrainspluginmcprefactoring.services.CallRefactorService
import com.github.harineko0.jetbrainspluginmcprefactoring.services.McpLifecycleService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class RefactorMcpServerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Register the notification group if it doesn't exist (optional, but good practice)
        // NotificationGroupManager.getInstance().registerNotificationGroup(
        //     NotificationGroup(NOTIFICATION_GROUP_ID, NotificationDisplayType.BALLOON, true)
        // )
        val myToolWindow = RefactorMcpServerToolWindow(toolWindow, project) // Pass project to MyToolWindow
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    // Pass project to the constructor
    class RefactorMcpServerToolWindow(toolWindow: ToolWindow, private val project: Project) {

        private val lifecycleService = project.service<McpLifecycleService>() // Rename for clarity
        private val callRefactorService = project.service<CallRefactorService>() // Get CallRefactorService instance

        fun getContent(): JBPanel<*> {
            // Main panel with vertical layout
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // Panel for Port configuration
            var port = 8080 // Default port
            val portPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JBLabel(MyBundle.message("portLabel", "Port:"))) // Assuming "portLabel" key exists or add it
                val portField = JBTextField("8080", 10) // Default port 8080, 10 columns wide
                // Note: Currently McpLifecycleService doesn't use a port.
                // This field is added for UI requirements but isn't connected to server logic yet.
                portField.toolTipText = "Port for MCP server (currently informational)"
                // get port from field
                portField.addActionListener {
                    port = portField.text.toIntOrNull() ?: 8080 // Default to 8080 if invalid
                }
                add(portField)
            }
            mainPanel.add(portPanel)
            mainPanel.add(Box.createRigidArea(Dimension(0, 10))) // Add fixed vertical space

            // Panel for Server control
            val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JBLabel(MyBundle.message("mcpServer", "MCP Server:"))) // Reuse existing key

                val toggleButton = JToggleButton(MyBundle.message("start", "Start")).apply {
                    // Set initial state based on whether the server might already be running
                    isSelected = lifecycleService.isServerRunning() // Use lifecycleService

                    addItemListener { e ->
                        val button = e.source as JToggleButton
                        if (button.isSelected) {
                            button.text = MyBundle.message("stop", "Stop")
                            thisLogger().info("Starting MCP server...")
                            lifecycleService.startServer(port) // Use lifecycleService
                            thisLogger().info("MCP server start initiated.")
                        } else {
                            button.text = MyBundle.message("start", "Start")
                            thisLogger().info("Stopping MCP server...")
                            lifecycleService.stopServer() // Use lifecycleService
                            thisLogger().info("MCP server stop initiated.")
                            // Show notification
                        }
                    }
                }

                add(toggleButton)
            }
            mainPanel.add(controlPanel)
            mainPanel.add(Box.createRigidArea(Dimension(0, 10))) // Add fixed vertical space


            // --- Panel for Demo of Refactoring ---
            val demoPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS) // Vertical layout for this section
                alignmentX = LEFT_ALIGNMENT // Align this panel to the left within mainPanel

                val filePath = "/Users/hari/proj/GG/Caller.dart"
                val codeToSymbol = "class C"

                // Button Row
                val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    val findButton = JButton("Find Usages").apply {
                        addActionListener {
                            try {
                                // Execute findUsage - consider running in background if potentially long
                                val res = callRefactorService.findUsage(filePath, codeToSymbol)
                                if (res.isOk) {
                                    val usages = res.value
                                    thisLogger().info("Found ${usages.size} usages.")
                                    // Log details
                                    usages.forEach { usage ->
                                        thisLogger().info("  - ${usage.filePath}:${usage.lineNumber}:${usage.columnNumber} : ${usage.usageTextSnippet}")
                                    }
                                }
                            } catch (ex: Exception) {
                                thisLogger().error("Error during Find Usages: ${ex.message}", ex)
                            }
                        }
                    }
                    val moveButton = JButton("Move...").apply {
                        addActionListener {
                            try {
                                val target = "/Users/hari/proj/GG/test"
                                val result = callRefactorService.moveElement(filePath, codeToSymbol, target)
                                thisLogger().info("Move element ${if (result.isOk) "successful" else "failed"}")
                            } catch (ex: Exception) {
                                thisLogger().error("Error during manual Find Usages: ${ex.message}", ex)
                            }
                        }
                    }
                    val deleteButton = JButton("Delete").apply {
                        addActionListener {
                            try {
                                callRefactorService.deleteElement(filePath, codeToSymbol)
                                thisLogger().info("Delete element successful")
                            } catch (ex: Exception) {
                                thisLogger().error("Error during manual Find Usages: ${ex.message}", ex)
                            }
                        }
                    }
                    val renameButton = JButton("Rename").apply {
                        addActionListener {
                            try {
                                val newName = "NewClassName"
                                callRefactorService.renameElement(filePath, codeToSymbol, newName)
                                thisLogger().info("Rename element successful")
                            } catch (ex: Exception) {
                                thisLogger().error("Error during manual Rename Element: ${ex.message}", ex)
                            }
                        }
                    }
                    val moveFileButton = JButton("Move File").apply {
                        addActionListener {
                            try {
                                val srcFilePath = "/Users/hari/proj/GG/Caller.dart" // Keep consistent
                                val dstDirPath = "/Users/hari/proj/GG/test" // Keep consistent
                                val result = callRefactorService.moveFile(srcFilePath, dstDirPath)
                                thisLogger().info("Move file ${if (result.isOk) "successful" else "failed"}")
                            } catch (ex: Exception) {
                                thisLogger().error("Error during manual Move File: ${ex.message}", ex)
                            }
                        }
                    }
                    val renameFileButton = JButton("Rename File").apply {
                        addActionListener {
                            try {
                                val sourceFilePath = "/Users/hari/proj/GG/Caller.dart" // Keep consistent
                                val newFileName = "CallerRenamed.dart"
                                val result = callRefactorService.renameFile(sourceFilePath, newFileName)
                                if (result.isOk) {
                                    thisLogger().info("Rename file successful.")
                                } else {
                                    thisLogger().error("Error during Rename File: ${result.error}")
                                }
                            } catch (ex: Exception) { // Catch unexpected errors
                                thisLogger().error("Unexpected error during Rename File action: ${ex.message}", ex)
                            }
                        }
                    }
                    val deleteFileButton = JButton("Delete File").apply { // New button
                        addActionListener {
                            try {
                                val targetFilePath = "/Users/hari/proj/GG/CallerRenamed.dart" // Use a specific file for deletion test
                                val result = callRefactorService.deleteFile(targetFilePath)
                                if (result.isOk) {
                                    thisLogger().info("Delete file successful.")
                                } else {
                                    thisLogger().error("Error during Delete File: ${result.error}")
                                }
                            } catch (ex: Exception) { // Catch unexpected errors
                                thisLogger().error("Unexpected error during Delete File action: ${ex.message}", ex)
                            }
                        }
                    }
                    add(findButton)
                    add(moveButton) // Move Element
                    add(deleteButton) // Delete Element
                    add(renameButton) // Rename Element
                    add(moveFileButton) // Move File
                    add(renameFileButton) // Rename File
                    add(deleteFileButton) // Add the new Delete File button
                }
                add(buttonPanel)
            }
            mainPanel.add(demoPanel)
            // --- End Panel for Find Usages ---

            mainPanel.add(Box.createVerticalGlue()) // Push content to the top

            return mainPanel
        }
    }
}
