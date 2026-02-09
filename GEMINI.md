# KeetQuiet - Voice Transcriber

KeetQuiet is an Android application designed to provide privacy-focused, offline voice transcription. It leverages on-device machine learning models (via `sherpa-onnx`) to convert speech to text without sending audio data to the cloud. The app is specifically optimized to integrate with other communication apps (like WhatsApp and Signal), allowing users to share audio files directly to KeetQuiet for immediate transcription.

## Project Structure

*   **Type:** Android Application (Kotlin)
*   **Package Name:** `com.example.voicetranscriber`
*   **Build System:** Gradle
*   **Min SDK:** 24
*   **Target SDK:** 34

### Key Technologies

*   **Language:** Kotlin
*   **Dependency Injection:** Hilt
*   **Concurrency:** Kotlin Coroutines & Flow
*   **Database:** Room (for history storage)
*   **ML Engine:** `sherpa-onnx` (for offline speech-to-text)
*   **Audio Processing:** `FFmpegKit` (for converting various audio formats to PCM)
*   **UI:** XML Layouts with ViewBinding, Single Activity Architecture (mostly)

### Directory Overview

*   `app/src/main/java/com/example/voicetranscriber`: Main source code.
    *   `MainActivity.kt`: The primary entry point for the user interface, hosting fragments for Recent transcriptions and History.
    *   `ShareReceiverActivity.kt`: A specialized, transparent-themed activity that handles `ACTION_SEND` intents. It intercepts audio files shared from other apps, performs the transcription, and displays the result.
    *   `di/`: Hilt modules for dependency injection.
    *   `data/`: Data layer including Room database and repositories.
    *   `audio/`: Audio conversion and processing logic.
    *   `model/`: Domain models and the `TranscriptionManager`.
    *   `ui/`: Fragments and UI-related classes (`HistoryFragment`, `RecentFragment`, `SettingsBottomSheet`).
*   `app/src/main/res`: Resources (layouts, strings, drawables).
*   `libs/`: Contains local libraries, specifically `sherpa-onnx.aar`.

## Building and Running

The project uses the standard Gradle wrapper.

### Build Commands

*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```

*   **Install Debug APK:**
    ```bash
    ./gradlew installDebug
    ```

*   **Run Unit Tests:**
    ```bash
    ./gradlew test
    ```

*   **Clean Build:**
    ```bash
    ./gradlew clean
    ```

### Release Signing

Release builds are signed using environment variables. If these are not present, it defaults to the standard Android debug keystore.
*   `SIGNING_KEYSTORE_PATH`
*   `SIGNING_KEYSTORE_PASSWORD`
*   `SIGNING_KEY_ALIAS`
*   `SIGNING_KEY_PASSWORD`

## Development Conventions

*   **Dependency Injection:** Always use Hilt for injecting dependencies. Annotate Android components (Activities, Fragments) with `@AndroidEntryPoint` and service classes with `@Inject`.
*   **Coroutines:** Use `lifecycleScope` for UI-bound asynchronous tasks. Collect flows safely within the lifecycle.
*   **Permissions:** Runtime permissions are handled explicitly (e.g., `READ_MEDIA_AUDIO` for Android 13+).
*   **Architecture:** Follows MVVM patterns. Logic should be separated into ViewModels and Repositories/Managers where possible, keeping Activities/Fragments focused on UI binding.
*   **Formatting:** Standard Kotlin coding conventions.

## How it Works

1.  **User Flow (App Launch):** The user opens the app (`MainActivity`) to view their transcription history or settings.
2.  **User Flow (Sharing):**
    *   A user receives a voice note in an app like WhatsApp.
    *   They tap "Share" and select "KeetQuiet".
    *   `ShareReceiverActivity` is launched.
    *   The app extracts the audio URI, converts it to the required PCM format using `FFmpegKit` (if necessary).
    *   The `TranscriptionManager` processes the PCM data using `sherpa-onnx`.
    *   The result is displayed to the user, who can then copy or share the text.

## Troubleshooting

*   **Audio Conversion Errors:** If transcription fails, check if `FFmpegKit` is correctly handling the input format (e.g., OGG/Opus from WhatsApp).
*   **Model Loading:** The app requires ML models. Ensure the logic for downloading or accessing these models (handled in `TranscriptionManager`) is functioning if they are not bundled.
