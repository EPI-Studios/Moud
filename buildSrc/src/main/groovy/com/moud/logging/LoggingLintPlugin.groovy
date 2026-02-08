package com.moud.logging

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class LoggingLintPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def task = project.tasks.register("logVolumeLint") {
            group = "verification"
            description = "Fails the build when debug logging volume exceeds the configured threshold."

            doLast {
                File srcDir = project.file("src/main/java")
                if (!srcDir.exists()) {
                    return
                }

                def thresholdProp = project.findProperty("moudLogDebugThreshold")
                int threshold = thresholdProp ? thresholdProp.toString().toInteger() : 120

                Map<File, Integer> offending = [:]
                project.fileTree(dir: srcDir, includes: ["**/*.java"]).each { File file ->
                    int count = 0
                    file.eachLine("UTF-8") { line ->
                        if (line.contains("LOGGER.debug(") || line.contains("logger.debug(")) {
                            count++
                        }
                    }
                    if (count > threshold) {
                        offending[file] = count
                    }
                }

                if (!offending.isEmpty()) {
                    StringBuilder message = new StringBuilder("Log volume lint failed:\n")
                    offending.each { file, count ->
                        message.append(" - ${project.relativePath(file)}: ${count} debug statements (threshold ${threshold})\n")
                    }
                    throw new GradleException(message.toString())
                }
            }
        }

        project.plugins.withId("java") {
            project.tasks.named("check").configure {
                dependsOn(task)
            }
        }
    }
}
