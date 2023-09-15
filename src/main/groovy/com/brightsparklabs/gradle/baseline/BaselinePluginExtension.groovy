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
    /** [Optional] The release deployment configuration. */
    DeployConfig deploy = new DeployConfig()
}

class DeployConfig {
    /** [Optional] The S3 bucket deployment configuration. */
    S3DeployConfig s3 = new S3DeployConfig()
}

class S3DeployConfig {
    /** The name of the S3 bucket to upload files to. */
    String bucketName
    /**
     * [Optional] The region of the S3 bucket. If unset, the AWS SDK will attempt to pull the
     * region from the system.
     */
    String region
    /** [Optional] The prefix to prepend to uploaded files. */
    String prefix = ""
    /** The paths of the files to upload to the S3 bucket. */
    Set<String> filesToUpload
}