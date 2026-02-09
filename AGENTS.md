# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app (`:app`) built with Gradle.
- Source code: `app/src/main/java/com/example/voicetranscriber`
- Resources: `app/src/main/res` (layouts, drawables, strings, themes)
- Local ML dependency: `app/libs/sherpa-onnx.aar`
- Build scripts: `build.gradle`, `settings.gradle`, `app/build.gradle`
- Generated output: `app/build/` and `.gradle/` (do not edit or commit)

Code is organized by responsibility: `ui/`, `data/`, `audio/`, `model/`, `domain/`, `di/`, `util/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:
- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew installDebug` installs the debug build on a connected device.
- `./gradlew lintDebug` runs Android lint checks for debug.
- `./gradlew testDebugUnitTest` runs JVM unit tests.
- `./gradlew connectedDebugAndroidTest` runs instrumentation tests on a device/emulator.
- `./gradlew clean` removes build artifacts.

## Coding Style & Naming Conventions
Follow Kotlin official style (`kotlin.code.style=official`):
- 4-space indentation, no tabs.
- `PascalCase` for classes (`TranscriptionManager`), `camelCase` for methods/properties, `UPPER_SNAKE_CASE` for constants.
- Keep UI orchestration in `ui/`, persistence in `data/`, and audio/transcription pipeline logic in `audio/`, `model/`, or `domain/`.
- Prefer constructor injection with Hilt; annotate Android entry points with `@AndroidEntryPoint`.

## Testing Guidelines
Current test dependencies are JUnit4 and `kotlinx-coroutines-test`.
- Place unit tests in `app/src/test/java/...`.
- Place instrumentation tests in `app/src/androidTest/java/...`.
- Name tests as `<ClassName>Test.kt`; use descriptive method names like `transcribe_emptyFile_returnsError`.
- Validate changes with `./gradlew testDebugUnitTest`; also run `connectedDebugAndroidTest` for Android-framework-heavy changes.

## Commit & Pull Request Guidelines
No local Git history is available in this workspace snapshot, so use Conventional Commit style:
- `feat(audio): add opus fallback path`
- `fix(ui): prevent duplicate dialog render`

Keep commits focused and small. PRs should include:
- What changed and why
- Linked issue (if available)
- Test evidence (commands run, relevant output)
- Screenshots/video for UI changes

## Security & Configuration Tips
Release signing reads environment variables: `SIGNING_KEYSTORE_PATH`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
Never commit keystores, credentials, `local.properties`, or logs containing personal audio/transcription data.
