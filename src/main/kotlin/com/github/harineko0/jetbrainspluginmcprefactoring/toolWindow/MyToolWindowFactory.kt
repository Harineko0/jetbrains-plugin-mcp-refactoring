package com.github.harineko0.jetbrainspluginmcprefactoring.toolWindow

import com.github.harineko0.jetbrainspluginmcprefactoring.MyBundle
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
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JToggleButton

class MyToolWindowFactory : ToolWindowFactory {

    // Define a notification group ID
    private val NOTIFICATION_GROUP_ID = "MCP Server Notifications"

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

        private val service = project.service<McpLifecycleService>() // Get service from passed project

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
                    // Set initial state based on whether the server might already be running (if possible)
                    // For simplicity, assume it starts as "Off" (not selected)
                    isSelected = service.isServerRunning() // Assuming McpLifecycleService has isServerRunning()

                    addItemListener { e ->
                        val button = e.source as JToggleButton
                        if (button.isSelected) {
                            button.text = MyBundle.message("stop", "Stop")
                            thisLogger().info("Starting MCP server...")
                            // val port = portField.text.toIntOrNull() ?: 8080 // Get port if needed later
                            service.startServer(port) // Pass port if service is updated
                            // TODO: Ideally, startServer should return status or use a callback
                            thisLogger().info("MCP server start initiated.")
                            // Show notification (assuming start is successful for now)
//                            showNotification(project,"MCP Server Started", NotificationType.INFORMATION)
                        } else {
                            button.text = MyBundle.message("start", "Start")
                            thisLogger().info("Stopping MCP server...")
                            service.stopServer() // Call the stop method
                            // TODO: Ideally, stopServer should return status or use a callback
                            thisLogger().info("MCP server stop initiated.")
                             // Show notification (assuming stop is successful for now)
//                            showNotification(project,"MCP Server Stopped", NotificationType.INFORMATION)
                        }
                    }
                }

                add(toggleButton)
            }
            mainPanel.add(controlPanel)

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
