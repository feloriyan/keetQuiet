# KeetQuiet - Offline Voice Message Transcription

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-24%2B-brightgreen.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)

KeetQuiet is an open-source Android app that transcribes voice messages from popular messaging apps using offline speech recognition powered by nvidia/parakeet-tdt-0.6b-v3. No internet connection required - your conversations stay private!

## Features

**Offline Transcription** - No internet required, all processing happens on-device
**Multi-App Support** - Works with WhatsApp, Telegram, Signal, and other messaging apps
**Privacy-Focused** - No data leaves your device, local-only processing


## Screenshots

<!-- Add screenshots here -->
<!-- ![Main Screen](screenshots/main.png) -->
<!-- ![Transcription](screenshots/transcription.png) -->
<!-- ![History](screenshots/history.png) -->

## Installation

### From Source3

```bash
# Clone the repository
git clone https://github.com/yourusername/KeetQuiet.git
cd KeetQuiet

# Build with Gradle
./gradlew assembleDebug

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- **Android 7.0+ (API 24+)**
- **Storage Permission** - To access voice message files
- **Internet Permission** - Only for downloading language models (optional)
- **~500MB Storage** - For speech recognition models

## Usage

### First Launch

1. **Download Models**: On first launch, the app will download required speech recognition models (~300MB)
2. **Grant Permissions**: Allow storage access to find voice messages
3. **Discovery**: The app will automatically scan for voice messages

### Transcribing Messages

1. **Browse Messages**: View discovered voice messages grouped by source app
2. **Filter Sources**: Use the chips to filter by WhatsApp, Telegram, Signal, or All
3. **Play Audio**: Tap a message to play the audio
4. **Transcribe**: Tap the transcribe button to convert speech to text
5. **View Results**: Transcription appears in a bottom sheet
6. **Save**: Transcriptions are automatically saved to history

## Privacy & Security

🔒 **Your data stays private**:
- All processing happens locally on your device
- No audio or text data is sent to any servers
- No internet connection required after model download
- No analytics or tracking of any kind

## Technical Details

### Architecture

- **MVVM Architecture** with Hilt dependency injection
- **Kotlin Coroutines** for asynchronous operations
- **Room Database** for local data persistence
- **Sherpa-ONNX with nvidia/parakeet-tdt-0.6b-v3** for offline speech recognition
- **FFmpeg** for audio format conversion

### Key Components

- **AudioConverter**: Handles audio format conversion to PCM
- **TranscriptionManager**: Manages the transcription workflow
- **VoiceMessageDiscovery**: Scans device for voice messages
- **ModelDownloader**: Handles language model downloads

### Supported Audio Formats

- **OPUS** (WhatsApp default)
- **OGG** (Telegram default)
- **M4A/AAC** (iMessage, Signal)
- **AMR** (Legacy formats)
- **WAV** (Uncompressed)
- **FLAC** (Lossless)
- **MP3** (Common format)

## Building from Source

### Prerequisites

- Android Studio (latest version)
- Android SDK (API 24+)
- Java 17+
- Kotlin 1.9.22+

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/KeetQuiet.git
   cd KeetQuiet
   ```

2. Open in Android Studio:
   - File → Open → Select the project directory
   - Let Android Studio sync Gradle dependencies

3. Build the project:
   - Build → Make Project
   - Or use command line: `./gradlew assembleDebug`

### Customization

- **Model Configuration**: Modify `TranscriptionManager.kt` for different models
- **Audio Formats**: Extend `AudioConverter.kt` for additional formats
- **UI Theming**: Adjust colors in `res/values/colors.xml`

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Ways to Contribute

- **Bug Reports**: Submit issues for bugs you encounter
- **Feature Requests**: Suggest new features
- **Code Contributions**: Submit pull requests
- **Translations**: Help translate the app
- **Documentation**: Improve docs and guides

## License

KeetQuiet is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for details.

### Key License Terms

- You may use, modify, and distribute the software freely
- Modified versions must also be open-source (GPL-3.0)
- No warranty is provided
- All derivative works must include source code

## Support

- **GitHub Issues**: For bug reports and feature requests
- **Discussions**: For general questions and ideas

## Credits

KeetQuiet uses these amazing open-source projects:

- **Sherpa-ONNX**: Offline speech recognition engine
- **FFmpeg**: Audio format conversion
- **Room**: SQLite database layer
- **Hilt**: Dependency injection
- **Kotlin Coroutines**: Asynchronous programming
- **nvidia/parakeet-tdt-0.6b-v3**: ASR-Modell für Speech-to-Text

## Donations

KeetQuiet is free and open-source. If you find it useful, consider:

- **Contributing code** to the project
- **Spreading the word** about the app
- **Supporting the developers**

💛 **Buy Me a Coffee**: [https://buymeacoffee.com/feloriyan](https://buymeacoffee.com/feloriyan)

Your support helps maintain and improve KeetQuiet! ☕

---

**KeetQuiet** - Your conversations, your privacy. 🔇

[GitHub](https://github.com/feloriyan/KeetQuiet)
