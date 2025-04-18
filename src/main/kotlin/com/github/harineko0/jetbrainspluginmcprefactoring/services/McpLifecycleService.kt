package com.github.harineko0.jetbrainspluginmcprefactoring.services

import com.github.harineko0.jetbrainspluginmcprefactoring.mcp.McpServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class McpLifecycleService(private val project: Project) : Disposable {

    private var ktorServerEngine:  EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine. Configuration>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val host = "127.0.0.1"
    private val port = 60051 // As requested

    fun startServer() {
        if (ktorServerEngine != null) {
            thisLogger().warn("MCP server start requested but already running for project ${project.name}")
            return
        }

        scope.launch {
            try {
                thisLogger().info("Starting MCP Ktor server for project ${project.name} on $host:$port")
                val mcpServerLogic = McpServer(project) // Configure MCP tools

                val engine = embeddedServer(CIO, port = port, host = host) {
                    mcp { mcpServerLogic.server } // Integrate MCP server
                }.start(wait = false) // Start non-blocking

                ktorServerEngine = engine
                thisLogger().info("MCP Ktor server started successfully for project ${project.name} on port $port.")

            } catch (e: Exception) {
                // Log specific bind exceptions differently?
                if (e is java.net.BindException) {
                     thisLogger().error("Failed to start MCP Ktor server for project ${project.name}: Port $port already in use.", e)
                } else {
                    thisLogger().error("Failed to start MCP Ktor server for project ${project.name}", e)
                }
                ktorServerEngine = null // Ensure engine is null if start failed
            }
        }
    }

    // Make stopServer public
    fun stopServer() {
        if (ktorServerEngine == null) {
            thisLogger().warn("MCP server stop requested but not running for project ${project.name}")
            return
        }
        thisLogger().info("Stopping MCP Ktor server for project ${project.name}...")
        // Run stop synchronously within the dispose method's context if needed,
        // but launching ensures it doesn't block the dispose thread excessively.
        // Consider if runBlocking is safer here if dispose needs completion guarantee.
        scope.launch {
            try {
                ktorServerEngine?.stop(1, 5, TimeUnit.SECONDS)
                thisLogger().info("MCP Ktor server stopped for project ${project.name}.")
            } catch (e: Exception) {
                thisLogger().error("Error stopping MCP Ktor server for project ${project.name}", e)
            } finally {
                ktorServerEngine = null
            }
        }
    }

    /**
     * Checks if the Ktor server engine is currently running (not null).
     * @return True if the server is considered running, false otherwise.
     */
    fun isServerRunning(): Boolean {
        return ktorServerEngine != null
    }

    override fun dispose() {
        thisLogger().info("Disposing McpLifecycleService for project ${project.name}, stopping server.")
        stopServer() // Call the public stopServer method
        // Cancel the scope to clean up any lingering coroutines
        scope.coroutineContext[Job]?.cancel()
        thisLogger().info("McpLifecycleService disposed for project ${project.name}.")
    }
}
