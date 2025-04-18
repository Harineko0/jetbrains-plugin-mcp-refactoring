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
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<McpLifecycleService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("mcpServer"))
            val startLabel = JBLabel(MyBundle.message("start"))
            val stopLabel = JBLabel(MyBundle.message("stop"))
            stopLabel.isEnabled = false

            add(label)
            add(JButton(MyBundle.message("start")).apply {
                addActionListener {
                    thisLogger().info("Starting MCP server...")
                    service.startServer()
                    thisLogger().info("MCP server started.")
                    startLabel.isEnabled = false
                    stopLabel.isEnabled = true
                }
            })
            add(JButton(MyBundle.message("stop")).apply {
                addActionListener {
                    thisLogger().info("Stopping MCP server...")
//                    service.stopServer()
                    thisLogger().info("(Not implemented) MCP server stopped.")
                    startLabel.isEnabled = true
                    stopLabel.isEnabled = false
                }
            })
        }
    }
}
