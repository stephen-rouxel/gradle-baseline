/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.baseline

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import spock.lang.Specification

/**
 * Unit tests for {@link BaselinePlugin}.
 */
public class BaselinePluginTest extends Specification {

    def "plugin registers task"() {
        given:
        def project = ProjectBuilder.builder().withName(BaselinePlugin.TEST_PROJECT_NAME).build()

        when:
        project.plugins.apply("com.brightsparklabs.gradle.baseline")

        then:
        //project.tasks.findByName("jacocoTestReport") != null
        // spotless
        project.tasks.findByName("spotlessApply") != null
        project.tasks.findByName("spotlessCheck") != null
        // owasp
        project.tasks.findByName("dependencyCheckAnalyze") != null
        // stale dependencies
        project.tasks.findByName("dependencyUpdates") != null
        project.tasks.findByName("useLatestVersions") != null
        // shadow
        project.tasks.findByName("shadowJar") != null
        // licence report
        project.tasks.findByName("generateLicenseReport") != null
    }
}
