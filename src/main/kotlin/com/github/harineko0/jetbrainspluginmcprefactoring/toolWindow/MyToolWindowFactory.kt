package com.github.harineko0.jetbrainspluginmcprefactoring.toolWindow

import com.github.harineko0.jetbrainspluginmcprefactoring.MyBundle
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
import java.awt.FlowLayout // Import layout manager
import javax.swing.BoxLayout // Import layout manager
import javax.swing.JButton // Keep for potential future use? No, replace with JToggleButton
import javax.swing.JToggleButton // Import ToggleButton
import javax.swing.JPanel // Import JPanel for better layout control

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<McpLifecycleService>()

        fun getContent(): JBPanel<*> {
            // Main panel with vertical layout
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // Panel for Port configuration
            val portPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JBLabel(MyBundle.message("portLabel", "Port:"))) // Assuming "portLabel" key exists or add it
                val portField = JBTextField("8080", 10) // Default port 8080, 10 columns wide
                // Note: Currently McpLifecycleService doesn't use a port.
                // This field is added for UI requirements but isn't connected to server logic yet.
                portField.toolTipText = "Port for MCP server (currently informational)"
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
                            service.startServer()
                            thisLogger().info("MCP server started.")
                        } else {
                            button.text = MyBundle.message("start", "Start")
                            thisLogger().info("Stopping MCP server...")
                            service.stopServer() // Call the stop method
                            thisLogger().info("MCP server stopped.")
                        }
                    }
                }
                // Set initial text based on state
                 if (toggleButton.isSelected) {
                     toggleButton.text = MyBundle.message("stop", "Stop")
                 } else {
                     toggleButton.text = MyBundle.message("start", "Start")
                 }

                add(toggleButton)
            }
            mainPanel.add(controlPanel)

            return mainPanel
        }
    }
}

// Helper function (assuming it exists or needs to be added in McpLifecycleService)
// fun McpLifecycleService.isServerRunning(): Boolean {
//     // TODO: Implement logic to check if the server instance is active
//     return false // Placeholder
// }
