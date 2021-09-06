package com.brightsparklabs.gradle.baseline.tasks

import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import groovy.json.JsonOutput

@CacheableTask
class CheckLicenseManageTempFileTask extends DefaultTask{
    private File projectDir = getProject().projectDir
    private File tempDir = getProject().file("${getProject().buildDir}/tmp/brightsparklabs/baseline")

    CheckLicenseManageTempFileTask() {
        group = "brightSPARK Labs - Baseline"
        description = "Auto-runs with the 'bslCheckLicense' task. This generates a temp allowedLicenses.json file for the checkLicense task if no override file is provided."
    }

    @InputFile
    File allowedLicensesFileJSON() {
        new File("${projectDir}/brightsparklabs/baseline/allowed-licenses.json")
    }

    @OutputFile
    File tempAllowedLicensesFileJSON() {
        return new File("${tempDir}/allowed-licenses.json")
    }

    @TaskAction
    void checkIfUsingTempAllowedLicensesJSON() {
        File tempFile = tempAllowedLicensesFileJSON()

        if (!allowedLicensesFileJSON().exists()) {
            // Generates the temp file if a override file doesn't exist
            tempDir.mkdirs()
            // Converts the baseline allowed-licenses template file into a JSON String
            String jsonString = JsonOutput.toJson(
                new JsonSlurper().setType(JsonParserType.LAX).parse(
                    getClass().getResourceAsStream("/allowed-licenses.json")
                )
            )

            tempFile.write(jsonString)

            // Overrides the licenseReport config to use the temp file
            getProject().licenseReport {
                allowedLicensesFile = tempFile
            }
            tempFile.deleteOnExit()
        } else {
            // Delete the existing temp file if a override file exists
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
