/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.baseline

import com.github.jk1.license.filter.LicenseBundleNormalizer
import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task

/**
 * The brightSPARK Labs Baseline Plugin.
 */
public class BaselinePlugin implements Plugin<Project> {

    /** Project name of the test-case which specifically needs to skip loading ErrorProne */
    public static final String TEST_PROJECT_NAME = "BaselinePluginTest-ProjectName"

    public void apply(Project project) {
        // set general properties
        project.group = "com.brightsparklabs"

        def versionProcess = "git describe --always --dirty".execute()
        versionProcess.waitFor()
        project.version = versionProcess.exitValue() == 0 ? versionProcess.text.trim() : "0.0.0-UNKNOWN"

        // enforce standards
        includeVersionInJar(project)
        includeDefaultAllowedLicensesInJar(project)
        setupCodeFormatter(project)
        setupStaleDependencyChecks(project)
        setupTestCoverage(project)
        setupVulnerabilityDependencyChecks(project)
        setupShadowJar(project)
        setupDependencyLicenseReport(project)

        /*
         ErrorProne cannot be loaded dynamically in our test case due to a class-loading exception
         The exception with the missing class is:
         java.lang.NoClassDefFoundError: org/gradle/kotlin/dsl/ConfigurationExtensionsKt
         This needs to be loaded via the `afterEvaluate` phase of Gradle, as it needs to be
         loaded via `dependences.errorprone` which is only available after loading the plugin.
         With the way our test-cases run, we try to load the plugins dynamically which is
         incompatible with loading the dependency via `afterEvaluate`.
         Therefore we disable this plugin from being loaded *specifically* in the test case.
         */
        if (! project.getName().equals(TEST_PROJECT_NAME)) {
            setupCodeQuality(project)
        }
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    private void includeVersionInJar(Project project) {
        def baselineDir = project.file("${project.buildDir}/bslBaseline")
        baselineDir.mkdirs()
        def versionFile = project.file("${project.buildDir}/bslBaseline/VERSION")
        versionFile.text = project.version

        project.afterEvaluate {
            if (project.tasks.findByName('processResources')) {
                project.processResources {
                    from(versionFile)
                }
            }
        }
    }

    private void includeDefaultAllowedLicensesInJar(Project project) {
        def baselineDir = project.file("${project.buildDir}/bslBaseline")
        baselineDir.mkdirs()
        def allowedLicensesFile = project.file("${project.buildDir}/bslBaseline/allowed-licenses.json")
        allowedLicensesFile.createNewFile()

        if (allowedLicensesFile.text.equals("")) {
            // Generates the baseline JSON of known acceptable licenses
            allowedLicensesFile.write(JsonOutput.prettyPrint(JsonOutput.toJson([
                allowedLicenses: [
                    [ moduleLicense: "MIT License" ],
                    [ moduleLicense: "Apache License, Version 2.0" ],
                    [ moduleLicense: "PUBLIC DOMAIN" ]
                ]
            ])))

            project.afterEvaluate {
                if (project.tasks.findByName('processResources')) {
                    project.processResources {
                        from(allowedLicensesFile)
                    }
                }
            }
        }
    }

    private void setupCodeFormatter(Project project) {
        project.plugins.apply "com.diffplug.spotless"
        addTaskAlias(project, project.spotlessApply)
        addTaskAlias(project, project.spotlessCheck)

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

            // Set globally-applied errorprone options here
            // Options are listed here: https://github.com/tbroyer/gradle-errorprone-plugin
            // Example disabling 'MissingSummary' warnings:
            /*
             project.tasks.named("compileTestJava").configure {
             options.errorprone.disable("MissingSummary")
             }
             project.tasks.named("compileJava").configure {
             options.errorprone.disable("MissingSummary")
             }
             */
        }
    }

    private void setupTestCoverage(final Project project) {
        project.plugins.apply "jacoco"

        project.afterEvaluate {
            project.jacocoTestReport.dependsOn 'test'
            addTaskAlias(project, project.jacocoTestReport)
        }
    }

    private void setupStaleDependencyChecks(final Project project) {
        project.plugins.apply "com.github.ben-manes.versions"
        addTaskAlias(project, project.dependencyUpdates)

        project.plugins.apply "se.patrikerdes.use-latest-versions"
        addTaskAlias(project, project.useLatestVersions)
        addTaskAlias(project, project.useLatestVersionsCheck)
    }

    private void setupVulnerabilityDependencyChecks(final Project project) {
        project.plugins.apply "org.owasp.dependencycheck"
        addTaskAlias(project, project.dependencyCheckAnalyze)
    }

    private void setupShadowJar(final Project project) {
        project.plugins.apply "java"
        project.plugins.apply "com.github.johnrengelman.shadow"
        addTaskAlias(project, project.shadowJar)
    }

    private void setupDependencyLicenseReport(final Project project) {
        project.plugins.apply "com.github.jk1.dependency-license-report"
        project.afterEvaluate {
            project.licenseReport {
                filters = [new LicenseBundleNormalizer(createDefaultTransformationRules: true)]
                allowedLicensesFile = new File("$project.buildDir/bslBaseline/allowed-licenses.json")
            }
        }
        addTaskAlias(project, project.generateLicenseReport)
        addTaskAlias(project, project.checkLicense)
    }

    /**
     * Creates a task alias nested under the BSL group for clarity.
     *
     * @param project Gradle Project to add the task to.
     * @param task Task to create an alias of.
     * @param alias Name of the alias.
     */
    private void addTaskAlias(final Project project, final Task task) {
        def aliasTaskName = 'bsl' + task.name.capitalize()
        def taskDescription = "${task.description.trim()}${task.description.endsWith('.') ? '':'.'} Alias for `${task.name}`."
        project.task(aliasTaskName) {
            group = "brightSPARK Labs - Baseline"
            description = taskDescription
        }
        project[aliasTaskName].dependsOn task
    }
}
