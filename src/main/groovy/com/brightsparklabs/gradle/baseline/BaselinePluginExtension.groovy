/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.baseline

/**
 * Configurable settings for the {@link BaselinePlugin}.
 */
class BaselinePluginExtension {
    /** [Optional] The license header to prefix each file with. */
    String licenseHeader = """/*
                             | * Maintained by brightSPARK Labs.
                             | * www.brightsparklabs.com
                             | *
                             | * Refer to LICENSE at repository root for license details.
                             | */
                           """.stripMargin("|")

    DeployConfig deploy = new DeployConfig()
}

class DeployConfig {
    S3DeployConfig s3 = new S3DeployConfig()
}

class S3DeployConfig {
    String bucketName
    String prefix = ""
    Set<String> filesToUpload
}