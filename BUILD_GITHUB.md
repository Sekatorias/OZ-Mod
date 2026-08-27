# Build through GitHub Actions

The local ChatGPT execution environment used for this revision did not have Gradle or outbound Maven/Fabric network access, so a valid Fabric Loom remapped JAR could not be produced locally.

This project contains `.github/workflows/build.yml`.

1. Put the project at the root of a GitHub repository.
2. Push to `main`/`master`, or run **Actions -> Build OZ Fabric 1.20.1 -> Run workflow**.
3. The workflow uses Java 17 and Gradle 8.7, runs `gradle clean build --stacktrace`, and uploads the remapped JAR as the `oz-origins-zaruba-1.20.1` artifact.

Do not distribute a plain `jar` made without Fabric Loom; it will not be correctly remapped for Fabric Loader.
