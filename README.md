# gradle-baseline

Applies brightSPARK Labs standardisation to gradle projects.

## Build

```shell
./gradlew build

# publish
./gradlew publishPlugins
```

## Usage

```groovy
// file: build.gradle

plugins {
    id 'com.brightsparklabs.gradle.baseline'
}
```

## Testing during development

To test plugin changes during development, use an alternative repository that
references this plugin, and do the following:

- Remove the version from the plugin definition in `build.gradle`
- Run `gradlew` tasks with `--include-build $BASELINE_PLUGIN_REPO`. i.e.

```bash
# Within repository that references the baseline-plugin
./gradlew --include-build $PATH_TO_BASELINE_REPO/gradle-baseline $TASK
```

## Bundled Plugins

The following plugins are currently bundled in automatically:

- [Spotless](https://plugins.gradle.org/plugin/com.diffplug.gradle.spotless)
  for formatting.
    - `spotlessCheck` to check code.
    - `spotlessApply` to update code.
- [SpotBugs](https://plugins.gradle.org/plugin/com.github.spotbugs) for static
  code analysis.
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

## Licenses

Refer to the `LICENSE` file for details.
