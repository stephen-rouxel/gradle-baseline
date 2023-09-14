/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.baseline

import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.google.common.base.Strings
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The brightSPARK Labs Baseline Plugin.
 */
public class BaselinePlugin implements Plugin<Project> {
    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    /** Project name of the test-case which specifically needs to skip loading ErrorProne */
    public static final String TEST_PROJECT_NAME = "BaselinePluginTest-ProjectName"

    /** Version of error_prone_core to add to all projects. For details of why this needs to be
     * added, refer to the errorprone plugin's `README`. */
    private static final String ERRORPRONE_CORE_VERSION = '2.20.0'

    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    /** Directory this plugin stores it generated files to. */
    File baselineBuildDir

    /** Directory this plugin looks for override files in. */
    File baselineOverrideDir

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    public void apply(Project project) {
        this.baselineBuildDir = new File("${project.buildDir}/brightsparklabs/baseline/")
        this.baselineBuildDir.mkdirs()
        this.baselineOverrideDir = new File("${project.projectDir}/brightsparklabs/baseline/")
        // NOTE: Do not create `this.baselineOverrideDir` here as it is noise if empty.
        //       Create only when files need to be written to it.

        // set general properties
        project.group = "com.brightsparklabs"

        def versionProcess = "git describe --always --dirty".execute()
        versionProcess.waitFor()
        project.version = versionProcess.exitValue() == 0 ? versionProcess.text.trim() : "0.0.0-UNKNOWN"

        def config = project.extensions.create("bslBaseline", BaselinePluginExtension)

        // Enforce standards.
        includeVersionInJar(project)
        setupCodeFormatter(project, config)
        setupStaleDependencyChecks(project)
        setupTestCoverage(project)
        setupVulnerabilityDependencyChecks(project)
        setupShadowJar(project)
        setupDependencyLicenseReport(project)
        setupDeployment(project, config)

        /*
         * ErrorProne cannot be loaded dynamically in our test case due to a class-loading exception
         *
         * The exception with the missing class is:
         *
         *   java.lang.NoClassDefFoundError: org/gradle/kotlin/dsl/ConfigurationExtensionsKt
         *
         * This needs to be loaded via the `afterEvaluate` phase of Gradle, as it needs to be
         * loaded via `dependences.errorprone` which is only available after loading the plugin.
         * With the way our test-cases run, we try to load the plugins dynamically which is
         * incompatible with loading the dependency via `afterEvaluate`.
         *
         * Therefore we disable this plugin from being loaded *specifically* in the test case.
         */
        if (!project.getName().equals(TEST_PROJECT_NAME)) {
            setupCodeQuality(project)
        }
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    private void includeVersionInJar(Project project) {
        def versionFile = project.file("${this.baselineBuildDir}/VERSION")
        versionFile.text = project.version

        project.afterEvaluate {
            if (project.tasks.findByName('processResources')) {
                project.processResources {
                    from(versionFile)
                    // Required by Gradle 7.
                    duplicatesStrategy 'include'
                }
            }
        }
    }

    private void setupCodeFormatter(Project project, BaselinePluginExtension config) {
        project.plugins.apply "com.diffplug.spotless"
        addTaskAlias(project, project.spotlessApply)
        addTaskAlias(project, project.spotlessCheck)

        project.afterEvaluate {
            // NOTE: config is only available after project is evaluated, so retrieve in this block.
            def header = config.licenseHeader

            project.spotless {
                // Always format Gradle files.
                groovyGradle {
                    greclipse()
                    indentWithSpaces(4)

                    // Allow formatting to be disabled via: `spotless:off` / `spotless:on` comments.
                    toggleOffOn()
                }

                if (isJavaProject(project)) {
                    java {
                        licenseHeader(header)
                        googleJavaFormat().aosp()

                        // Allow formatting to be disabled via: `spotless:off` / `spotless:on` comments.
                        toggleOffOn()
                    }
                }

                if (isGroovyProject(project)) {
                    groovy {
                        licenseHeader(header)
                        // excludes all Java sources within the Groovy source dirs
                        excludeJava()

                        greclipse()
                        indentWithSpaces(4)

                        // Allow formatting to be disabled via: `spotless:off` / `spotless:on` comments.
                        toggleOffOn()
                    }
                }
            }
        }
    }

    private void setupCodeQuality(Project project) {
        project.plugins.apply "net.ltgt.errorprone"

        project.afterEvaluate {
            if (!isJavaProject(project) && !isGroovyProject(project)) {
                // Nothing to configure plugin for.
                return
            }

            // Ensure a repository is defined so the errorprone dependency below  can be obtained.
            project.repositories { mavenCentral() }

            // This needs to be added to all projects which want to use errorprone. For details
            // refer to the errorprone plugin's `README`.
            project.dependencies {
                errorprone("com.google.errorprone:error_prone_core:${ERRORPRONE_CORE_VERSION}")
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
            project.tasks.named("compileJava").configure {
                // Warnings in generated code are irrelevant, ignore them.
                options.errorprone.disableWarningsInGeneratedCode = true
            }
        }
    }

    private void setupTestCoverage(final Project project) {
        project.plugins.apply "jacoco"

        project.afterEvaluate {
            if (!isJavaProject(project) && !isGroovyProject(project)) {
                // Nothing to configure plugin for.
                return
            }

            // Task will only exist if its a Java project.
            if (project.tasks.findByName('jacocoTestReport')) {
                project.jacocoTestReport.dependsOn 'test'
                addTaskAlias(project, project.jacocoTestReport)
            }
        }
    }

    private void setupStaleDependencyChecks(final Project project) {
        project.plugins.apply "com.github.ben-manes.versions"

        def isUnstable = { String version ->
            // Versions are deemed unstable if the version string contains a pre-release flag.
            return version ==~ /(?i).*-(alpha|beta|rc|cr|m|pre|).*/
        }

        // Only use new dependencies versions if they are a stable release.
        project.tasks.named("dependencyUpdates").configure {
            rejectVersionIf {
                isUnstable(it.candidate.version)
            }
        }

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
        addTaskAlias(project, project.generateLicenseReport)
        addTaskAlias(project, project.checkLicense)

        project.afterEvaluate {
            project.licenseReport {
                filters = [
                    new LicenseBundleNormalizer(createDefaultTransformationRules: true)
                ]
                allowedLicensesFile = new File("${baselineBuildDir}/allowed-licenses.json")
            }

            project.checkLicense.dependsOn project.bslGenerateAllowedLicenses
        }

        // ---------------------------------------------------------------------
        // Add custom tasks for working with the plugin's configuration file.
        // ---------------------------------------------------------------------

        /*
         * The checkLicense task requires a java.io.File type as an input, this task manages the
         * creation of this input file as a temp file in the build directory. If an override file
         * exists (see bslOverrideAllowedLicenses task) it will use that, otherwise it will use the
         * default configuration from `allowed-licenses.json` in the resources.
         */
        project.task("bslGenerateAllowedLicenses") {
            group = "brightSPARK Labs - Baseline"
            description = "Generates the configuration file for the `checkLicense` task using " +
                    "either an override file (if it exists) or the default baseline " +
                    "configuration file."

            inputs.files("${this.baselineOverrideDir}/allowed-licenses.json").optional()
            outputs.file("${this.baselineBuildDir}/allowed-licenses.json")

            doLast {
                String allowedLicensesConfig = inputs.files.singleFile.exists()
                        // Use the exsiting override file (JsonSlurper used to check it is valid JSON).
                        ? JsonOutput.toJson(new JsonSlurper().parseText(inputs.files.singleFile.text))
                        // No override file, use the default.
                        : getClass().getResourceAsStream("/allowed-licenses.json").getText();

                outputs.files.singleFile.text = allowedLicensesConfig
            }
        }

        // Add a task for easily creating an override file.
        project.task("bslOverrideAllowedLicenses") {
            group = "brightSPARK Labs - Baseline"
            description = "Creates an override file for the types of allowed licenses that " +
                    "dependencies can have. This config file is used by the `checkLicense` task."

            outputs.file("${this.baselineOverrideDir}/allowed-licenses.json")

            doLast {
                // Only create the directory if there is something to put in it.
                this.baselineOverrideDir.mkdirs()

                // Seed the file with the default configuration.
                def outputFile = outputs.files.singleFile
                outputFile.text = getClass().getResourceAsStream("/allowed-licenses.json").getText()

                logger.lifecycle("Override file created at: [${outputFile}]")
            }
        }
    }

    private void setupDeployment(final Project project, final BaselinePluginExtension config) {
        project.afterEvaluate {
            // NOTE: config is only available after project is evaluated, so retrieve in this block.
            setupDeployToS3(project, config.deploy.s3)
        }
    }

    private void setupDeployToS3(final Project project, final S3DeployConfig s3DeployConfig) {
        final String bucketName = s3DeployConfig.bucketName
        final String prefix = s3DeployConfig.prefix
        final Set<String> filesToUpload = s3DeployConfig.filesToUpload

        project.tasks.register("bslDeployToS3") {
            group = "brightSPARK Labs - Baseline"
            description = "Deploys built files to S3."

            if (Strings.isNullOrEmpty(bucketName) || filesToUpload == null || filesToUpload.
                    isEmpty()) {
                logger.lifecycle("Aborted task `${name}`. Missing configuration.")
                return
            }

            final Region region = Region.AP_SOUTHEAST_2;
            final S3Client s3 = S3Client.builder()
                    .region(region)
                    .build()

            try {
                for (String file : filesToUpload) {
                    final Path filePath = Paths.get(file)
                    if (!Files.exists(filePath)) {
                        logger.error("No such file: `${filePath}`")
                        return
                    }

                    final String fileName = getPrefixedFileName(filePath, prefix)

                    final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileName)
                            .build() as PutObjectRequest

                    s3.putObject(putObjectRequest, RequestBody.fromFile(filePath.toFile()))
                    System.out.println("Successfully placed " + fileName +" into bucket "+bucketName)
                }
            } catch (NoSuchFileException e) {
                logger.error("No such file: "+e)
            } catch (S3Exception e) {
                logger.error(e.getMessage());
                throw e
            }
        }
    }

    private String getPrefixedFileName(final Path filePath, final String prefix) {
        final String fileName = filePath.getFileName().toString()
        if (fileName.startsWith(prefix)) {
            return fileName
        }
        return "${prefix}-${fileName}"
    }

    /**
     * Returns true if the project compiles Java code. Only reliable if called in `project.afterEvaluate`.
     *
     * @param project The project to check.
     * @return `true` if the `java` plugin has been applied.
     */
    private boolean isJavaProject(Project project) {
        return project.tasks.findByName('compileJava') != null
    }

    /**
     * Returns true if the project compiles Groovy code. Only reliable if called in `project.afterEvaluate`.
     *
     * @param project The project to check.
     * @return `true` if the `java` plugin has been applied.
     */
    private boolean isGroovyProject(Project project) {
        return project.tasks.findByName('compileGroovy') != null
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
