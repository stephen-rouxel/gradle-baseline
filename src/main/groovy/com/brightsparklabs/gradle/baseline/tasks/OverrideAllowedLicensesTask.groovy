package com.brightsparklabs.gradle.baseline.tasks

import groovy.json.JsonOutput
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
class OverrideAllowedLicensesTask extends DefaultTask{

    OverrideAllowedLicensesTask() {
        group = "brightSPARK Labs - Baseline"
        description = "Generates an override configuration file for types of allowed licenses."
    }

    private def baselineDir = getProject().file("${getProject().projectDir}/brightsparklabs/baseline")

    @OutputFile
    File allowedLicensesFileJSON() {
        new File("${baselineDir}/allowed-licenses.json")
    }

    @TaskAction
    void bslOverrideAllowedLicenses() {
        if (!allowedLicensesFileJSON().exists()) baselineDir.mkdirs()

        // Converts the baseline allowed-licenses template file into a human-readable JSON String
        String jsonString = JsonOutput.prettyPrint(JsonOutput.toJson(
            new JsonSlurper().setType(JsonParserType.LAX).parse(
                getClass().getResourceAsStream("/allowed-licenses.json")
            )
        ))

        allowedLicensesFileJSON().write(jsonString)
    }
}
