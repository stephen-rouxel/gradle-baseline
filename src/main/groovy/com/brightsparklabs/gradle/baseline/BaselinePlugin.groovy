/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.baseline


import com.github.jk1.license.filter.LicenseBundleNormalizer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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
        if (!project.getName().equals(TEST_PROJECT_NAME)) {
            setupCodeQuality(project)
        }
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    private void includeVersionInJar(Project project) {
        def baselineDir = project.file("${project.buildDir}/brightsparklabs/baseline")
        baselineDir.mkdirs()
        def versionFile = project.file("${baselineDir}/VERSION")
        versionFile.text = project.version

        project.afterEvaluate {
            if (project.tasks.findByName('processResources')) {
                project.processResources {
                    from(versionFile)
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

        def tmpBaselineDir = new File("${project.buildDir}/tmp/brightsparklabs/baseline/")
        def overrideBaselineDir = new File("${project.projectDir}/brightsparklabs/baseline/")

        /*
            Creates an override file with the default configuration from the internal baseline
            allowed-licenses.json file
        */
        project.task("bslOverrideAllowedLicenses") {
            group = "brightSPARK Labs - Baseline"
            description = "Creates an override file for the config of the types of allowed licenses that " +
                    "dependencies can have. This config file is used by the `checkLicense` task."

            outputs.file("${overrideBaselineDir}/allowed-licenses.json")

            doLast {
                if (!overrideBaselineDir.exists()) {
                    overrideBaselineDir.mkdirs()
                }

                outputs.files.singleFile.text = getClass().getResourceAsStream("/allowed-licenses.json").getText()
            }
        }

        /*
            The checkLicense task requires a java.io.File type as an input, this task manages the creation of this input
            file as a temp file in the build directory. It uses the configuration of the baseline allowed-licenses.json
            or an override configuration file from running the bslOverrideAllowedLicenses task.
        */
        project.task("bslGenerateAllowedLicenses") {
            group = "brightSPARK Labs - Baseline"
            description = "Generates the config file for the `checkLicense` task using either " +
                    "the default baseline config or a supplied config override file."

            inputs.files("${overrideBaselineDir}/allowed-licenses.json").optional()
            outputs.file("${tmpBaselineDir}/allowed-licenses.json")

            doLast {
                String allowedLicensesConfig
                def jsonSlurper = new JsonSlurper()

                if (inputs.files.singleFile.exists()) {
                    allowedLicensesConfig = JsonOutput.toJson(jsonSlurper.parseText(inputs.files.singleFile.text))
                } else {
                    allowedLicensesConfig = getClass().getResourceAsStream("/allowed-licenses.json").getText()
                }

                if (outputs.files.singleFile.exists()) {
                    if (outputs.files.singleFile.text == allowedLicensesConfig) {
                       return
                    }
                }
                outputs.files.singleFile.text = allowedLicensesConfig
            }
        }

        project.afterEvaluate {
            project.licenseReport {
                filters = [new LicenseBundleNormalizer(createDefaultTransformationRules: true)]
                allowedLicensesFile = new File("${tmpBaselineDir}/allowed-licenses.json")
            }

            project.checkLicense.dependsOn project.bslGenerateAllowedLicenses
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
        def taskDescription = "${task.description.trim()}${task.description.endsWith('.') ? '' : '.'} Alias for `${task.name}`."
        project.task(aliasTaskName) {
            group = "brightSPARK Labs - Baseline"
            description = taskDescription
        }
        project[aliasTaskName].dependsOn task
    }
}
