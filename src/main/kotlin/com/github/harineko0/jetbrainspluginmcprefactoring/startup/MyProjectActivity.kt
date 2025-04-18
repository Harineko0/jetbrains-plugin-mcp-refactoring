package com.github.harineko0.jetbrainspluginmcprefactoring.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("Project startup activity executing for ${project.name}")
    }
}
