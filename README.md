# gradle-baseline

[![Build Status
master](https://api.travis-ci.org/brightsparklabs/gradle-baseline.svg?branch=master)](https://travis-ci.org/brightsparklabs/gradle-baseline)
[![Gradle Plugin](https://img.shields.io/badge/gradle-latest-blue)](https://plugins.gradle.org/plugin/com.brightsparklabs.gradle.baseline)

Applies brightSPARK Labs standardisation to gradle projects.

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

# setup git (plugin requires repo to be nder git control)
git init
git add .
git commit "Initial commit"
git tag -a -m "Tag v0.0.0" 0.0.0

# run using the development version of the plugin
gradlew --include-build /path/to/gradle-baseline <task>
```

## Features

- Adds a `VERSION` file to the root of the JAR containing the project version.

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

## Licenses

Refer to the `LICENSE` file for details.
