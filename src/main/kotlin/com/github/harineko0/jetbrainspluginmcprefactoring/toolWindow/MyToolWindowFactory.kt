package com.github.harineko0.jetbrainspluginmcprefactoring.toolWindow

import com.github.harineko0.jetbrainspluginmcprefactoring.MyBundle
import com.github.harineko0.jetbrainspluginmcprefactoring.services.CallRefactorService // Import CallRefactorService
import com.github.harineko0.jetbrainspluginmcprefactoring.services.McpLifecycleService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToggleButton

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Register the notification group if it doesn't exist (optional, but good practice)
        // NotificationGroupManager.getInstance().registerNotificationGroup(
        //     NotificationGroup(NOTIFICATION_GROUP_ID, NotificationDisplayType.BALLOON, true)
        // )
        val myToolWindow = MyToolWindow(toolWindow, project) // Pass project to MyToolWindow
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    // Pass project to the constructor
    class MyToolWindow(toolWindow: ToolWindow, private val project: Project) {

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
                            // Show notification
                            showNotification(project, "MCP Server Started", NotificationType.INFORMATION)
                        } else {
                            button.text = MyBundle.message("start", "Start")
                            thisLogger().info("Stopping MCP server...")
                            lifecycleService.stopServer() // Use lifecycleService
                            thisLogger().info("MCP server stop initiated.")
                            // Show notification
                            showNotification(project, "MCP Server Stopped", NotificationType.INFORMATION)
                        }
                    }
                }

                add(toggleButton)
            }
            mainPanel.add(controlPanel)


            // --- Panel for Find Usages ---
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
                                val usages = callRefactorService.findUsage(filePath, codeToSymbol)
                                thisLogger().info("Found ${usages.size} usages.")
                                // Log details
                                usages.forEach { usage ->
                                    thisLogger().info("  - ${usage.filePath}:${usage.lineNumber}:${usage.columnNumber} : ${usage.usageTextSnippet}")
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
                                thisLogger().info("Move element ${if (result) "successful" else "failed"}")
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
                                thisLogger().error("Error during manual Find Usages: ${ex.message}", ex)
                            }
                        }
                    }
                    add(findButton)
                    add(moveButton)
                    add(deleteButton)
                    add(renameButton)
                }
                add(buttonPanel)
            }
            mainPanel.add(demoPanel)
            // --- End Panel for Find Usages ---


            return mainPanel
        }


        // Function to show notification
        private fun showNotification(project: Project, content: String, type: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("MCP Server Notifications") // Use the same ID defined above or a known one
                .createNotification(content, type)
                .notify(project)
        }
    }
}
