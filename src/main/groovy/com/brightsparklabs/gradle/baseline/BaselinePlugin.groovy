/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details
 */

package com.brightsparklabs.gradle.baseline

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * The brightSPARK Labs Baseline Plugin.
 */
public class BaselinePlugin implements Plugin<Project> {
    public void apply(Project project) {
        // set general properties
        project.group = "com.brightsparklabs"

        def versionProcess = "git describe --always --dirty".execute()
        versionProcess.waitFor()
        project.version = versionProcess.exitValue() == 0 ? versionProcess.text.trim() : "0.0.0-UNKNOWN"

        // enforce standards
        setupCodeFormatter(project)
        setupCodeQuality(project)
        setupTestCoverage(project)
        setupStaleDependencyChecks(project)
        setupVulnerabilityDependencyChecks(project)
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    private void setupCodeFormatter(Project project) {
        project.plugins.apply "com.diffplug.gradle.spotless"

        def header = """/*
                       | * Maintained by brightSPARK Labs.
                       | * www.brightsparklabs.com
                       | *
                       | * Refer to LICENSE at repository root for license details.
                       | */
                     """.stripMargin("|")
        project.afterEvaluate {
            project.spotless {
                java {
                    licenseHeader(header)
                    googleJavaFormat().aosp()
                }
                groovyGradle {
                    // same as groovy, but for .gradle (defaults to "*.gradle")
                    greclipse()
                    indentWithSpaces(4)
                }

                if (project.plugins.hasPlugin("groovy")) {
                    groovy {
                        licenseHeader(header)
                        // excludes all Java sources within the Groovy source dirs
                        excludeJava()

                        greclipse()
                        indentWithSpaces(4)
                    }
                }
            }
        }
    }

    private void setupCodeQuality(Project project) {
        project.plugins.apply "net.ltgt.errorprone"
        project.afterEvaluate {
            project.dependencies {
                errorprone("com.google.errorprone:error_prone_core:2.4.0")
            }
        }
    }

    private void setupTestCoverage(final Project project) {
        project.plugins.apply "jacoco"
        project.afterEvaluate {
            project.jacocoTestReport {
                dependsOn 'test'
            }
        }
    }

    private void setupStaleDependencyChecks(final Project project) {
        project.plugins.apply "com.github.ben-manes.versions"
        project.plugins.apply "se.patrikerdes.use-latest-versions"
    }

    private void setupVulnerabilityDependencyChecks(final Project project) {
        project.plugins.apply "org.owasp.dependencycheck"
    }
}
