# gradle-baseline

[![Build Status](https://github.com/brightsparklabs/gradle-baseline/actions/workflows/unit_tests.yml/badge.svg)](https://github.com/brightsparklabs/gradle-baseline/actions/workflows/unit_tests.yml)
[![Gradle Plugin](https://img.shields.io/gradle-plugin-portal/v/com.brightsparklabs.gradle.baseline)](https://plugins.gradle.org/plugin/com.brightsparklabs.gradle.baseline)

Applies brightSPARK Labs standardisation to gradle projects.

**NOTE: This plugin requires JDK 17 or above and Gradle 8.**

## Build

Development Status: [![Build Status develop](https://api.travis-ci.org/brightsparklabs/gradle-docs.svg?branch=develop)](https://travis-ci.org/brightsparklabs/gradle-baseline)

```shell
./gradlew build

# publish
./gradlew publishPlugins
```

## Usage

```groovy
// file: build.gradle

plugins {
    id 'com.brightsparklabs.gradle.baseline' version '<version>'
}
```

## Configuration

Use the following configuration block to configure the plugin:

```groovy
// file: build.gradle

bslBaseline {
    /** [Optional] The license header to prefix each file with. Defaults to the below. */
    licenseHeader = """/*
                      | * Maintained by brightSPARK Labs.
                      | * www.brightsparklabs.com
                      | *
                      | * Refer to LICENSE at repository root for license details.
                      | */
                    """.stripMargin("|")
}
```

## Upgrade notes

To upgrade the dependencies of this project, in the base directory (which contains the
`build.gradle` file) run the following command:

```bash
./gradlew useLatestVersionsCheck
```

This will list all the gradle dependencies that can be upgraded, and after checking these you may
run:

```bash
./gradlew useLatestVersions
```

Which will update the `build.gradle` file to use the versions listed by the `useLatestVersionsCheck`
task.

In order to update the gradle version, you should refer to the relevant documentation provided by
gradle ([Example](https://docs.gradle.org/current/userguide/upgrading_version_7.html)).

```bash
# See deprecation warnings in the console.
gradle help --warning-mode=all
```
After addressing these warnings you can upgrade to the next version of gradle.

```bash
# Set gradle wrapper version.
gradle wrapper --gradle-version <VERSION>
```

When bumping dependencies the `ERRORPRONE_CORE_VERSION` variable in `BaselinePlugin.groovy` must
match the `error_prone_core` (not the `errorprone.gradle.plugin`) version, read about this in the
`errorprone.gradle.plugin` [README](https://github.com/tbroyer/gradle-errorprone-plugin).

This plugin should be tested on a local project before pushing, which can be done with the steps
in the *"Testing during development"* section.

## Testing during development

To test plugin changes during development:

```bash
# bash

# create a test application
mkdir gradle-baseline-test
cd gradle-baseline-test
gradle init --type java-application --dsl groovy
# add the plugin (NOTE: do not specify a version)
sed -i "/plugins/ a id 'com.brightsparklabs.gradle.baseline'" build.gradle

# setup git (plugin requires repo to be under git control)
git init
git add .
git commit "Initial commit"
git tag -a -m "Tag v0.0.0" 0.0.0

# run using the development version of the plugin
gradlew --include-build /path/to/gradle-baseline <task>
```

## Features

- Standardises the following:
    - Code formatting rules.
    - Static code analyser configuration.
    - Uber JAR packaging.
- Checks for dependency updates/vulnerabilities.
- Checks for allowed license on dependencies.
- Applies  a `VERSION` file to the root of the JAR containing the project version.

## Allowed Licenses

By default, only the following licenses for dependencies are allowed:

- MIT License
- Apache 2.0 License
- Public Domain License

This default list can be modified per-project by running the `bslOverrideAllowedLicenses` task to
expose the config file located at `/brightsparklabs/baseline/allowed-licenses.json`.

The Documentation for this JSON Format can be found within the [Licence Report
Docs](https://github.com/jk1/Gradle-License-Report#allowed-licenses-file).

## Bundled Plugins

The following plugins are currently bundled in automatically:

- [Spotless](https://plugins.gradle.org/plugin/com.diffplug.gradle.spotless)
  for formatting.
    - `spotlessCheck` to check code.
    - `spotlessApply` to update code.
- [Error Prone](https://plugins.gradle.org/plugin/net.ltgt.errorprone) for
  static code analysis.
- [Gradle
  Versions](https://plugins.gradle.org/plugin/com.github.ben-manes.versions)
  for stale dependency checks.
    - `dependencyUpdates` to check for updated dependencies.
- [Use Latest
  Versions](https://plugins.gradle.org/plugin/se.patrikerdes.use-latest-versions)
  plugins for dependency updates.
    - `useLatestVersions` to update dependencies in `build.gradle`.
    - `useLatestVersionsCheck` to check if the updates were applied correctly.
- [OWASP](https://plugins.gradle.org/plugin/org.owasp.dependencycheck) plugin
  for vulnerability dependency checks.
    - `dependencyCheckAnalyze` to check for vulnerabilities.
- [Shadow](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow) plugin
  enables the creation of fat jars.
    - `shadowJar` to generate fat jars.
- [License Report](https://plugins.gradle.org/plugin/com.github.jk1.dependency-license-report) for
  generating reports about the licenses of dependencies
    - `generateLicenseReport` to generate a license report.
    - `checkLicense` to verify the licenses of the dependencies are allowed.

## Licenses

Refer to the `LICENSE` file for details.
