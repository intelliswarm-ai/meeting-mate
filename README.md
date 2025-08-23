# MeetingMate - AI-Powered Meeting Transcription & Summarization

MeetingMate is an Android application that records meetings, automatically transcribes them using OpenAI's Whisper API, generates intelligent summaries using GPT, and integrates with your calendar to organize meeting notes.

## Features

- **Audio Recording**: High-quality meeting recording with background service support
- **AI Transcription**: Automatic speech-to-text using OpenAI Whisper API
- **Smart Summaries**: Generate structured meeting summaries with key points, action items, and decisions
- **Calendar Integration**: Link recordings to calendar events and save notes directly to your calendar
- **File-Based Storage**: Organized folder structure for easy access to transcripts and summaries
- **Secure API Key Storage**: Encrypted storage for OpenAI API credentials

## Project Structure

```
MeetingMate/
├── app/
│   └── src/main/java/ai/intelliswarm/meetingmate/
│       ├── data/
│       │   └── MeetingFileManager.java       # File-based storage system
│       ├── service/
│       │   ├── AudioRecordingService.java    # Audio recording service
│       │   ├── CalendarService.java          # Calendar integration
│       │   └── OpenAIService.java            # OpenAI API integration
│       ├── utils/
│       │   └── SettingsManager.java          # Secure settings storage
│       └── ui/
│           ├── home/                         # Recording interface
│           ├── dashboard/                    # Recordings list
│           └── notifications/                # Settings
```

## File Storage Structure

Meeting files are organized in the Documents/MeetingMate folder:

```
Documents/MeetingMate/
├── Meetings/
│   └── 2024/
│       └── 01-January/
│           └── 15/
│               ├── meeting_20240115_143000_metadata.json
│               ├── meeting_20240115_143000_transcript.txt
│               └── meeting_20240115_143000_summary.md
├── Audio/
│   └── meeting_20240115_143000.m4a
├── Transcripts/
│   └── meeting_20240115_143000_transcript.txt
└── Summaries/
    └── meeting_20240115_143000_summary.md
```

## Setup Instructions

### Prerequisites

1. Android Studio (latest version)
2. Android device or emulator (API 24+)
3. OpenAI API key

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/meeting-mate.git
cd meeting-mate
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Build and run the app on your device/emulator

### Configuration

1. **First Launch**: The app will prompt you to enter your OpenAI API key
2. **Permissions**: Grant the following permissions when prompted:
   - Microphone (for recording)
   - Storage (for saving recordings)
   - Calendar (optional, for calendar integration)

## Usage

### Recording a Meeting

1. Open the app and navigate to the Home tab
2. (Optional) Enter a meeting title
3. (Optional) Link to a calendar event by checking "Link to calendar event"
4. Tap the large "Start" button to begin recording
5. Use Pause/Resume during the meeting as needed
6. Tap "Stop" when the meeting ends

### Automatic Processing

After recording stops, the app will:
1. Save the audio file
2. Send it to OpenAI Whisper for transcription
3. Generate a meeting summary using GPT
4. Save all files in organized folders
5. (Optional) Update the linked calendar event with notes

### Viewing Past Meetings

1. Tap "View Past Recordings" on the home screen
2. Browse meetings by date
3. Search meetings by title
4. View transcripts and summaries
5. Share or export meeting notes

## API Integration

### OpenAI Services

The app uses two OpenAI APIs:

1. **Whisper API** for transcription:
   - Model: `whisper-1`
   - Supports multiple languages
   - Returns timestamped segments

2. **GPT API** for summarization:
   - Model: `gpt-4o-mini`
   - Generates structured summaries
   - Creates meeting titles

### API Key Management

API keys are stored securely using Android's EncryptedSharedPreferences. To update your API key:

1. Go to Settings (Notifications tab)
2. Tap "API Configuration"
3. Enter your new OpenAI API key

## Development

### Building from Source

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Creating Release APK for Google Play Store

#### 1. Generate Signing Key

First, create a keystore file to sign your release APK:

```bash
keytool -genkeypair -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias
```

You'll be prompted to enter:
- Keystore password (keep this secure!)
- Key password
- Your name and organization details

Store the keystore file (`my-release-key.jks`) in a secure location outside your project directory.

#### 2. Configure Gradle Signing

Add the following to your `app/build.gradle.kts` file:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../my-release-key.jks")
            storePassword = "YOUR_KEYSTORE_PASSWORD"
            keyAlias = "my-key-alias"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**Security Note**: For production builds, use environment variables or a separate `keystore.properties` file instead of hardcoding passwords:

Create `keystore.properties` in your project root:
```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=my-key-alias
storeFile=../my-release-key.jks
```

Then update your `build.gradle.kts`:
```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
}
```

#### 3. Build Signed Release APK

```bash
# Build signed release APK
./gradlew assembleRelease

# Build Android App Bundle (AAB) for Play Store
./gradlew bundleRelease
```

The signed APK will be located at: `app/build/outputs/apk/release/app-release.apk`
The AAB file will be located at: `app/build/outputs/bundle/release/app-release.aab`

**Important**: Always use the AAB format for Google Play Store uploads as it provides better optimization and smaller download sizes.

### Key Dependencies

- **Retrofit**: HTTP client for API calls
- **OkHttp**: Network layer
- **Room**: Local database (optional, for future features)
- **WorkManager**: Background task scheduling
- **Security Crypto**: Encrypted preferences
- **Dexter**: Runtime permissions

### Adding New Features

1. **Custom Prompts**: Modify `OpenAIService.java` to customize summary format
2. **Audio Quality**: Adjust settings in `AudioRecordingService.java`
3. **File Organization**: Customize folder structure in `MeetingFileManager.java`

## Troubleshooting

### Common Issues

1. **Recording doesn't start**
   - Check microphone permissions in Settings > Apps > MeetingMate
   - Ensure no other app is using the microphone

2. **Transcription fails**
   - Verify your OpenAI API key is valid
   - Check internet connection
   - Ensure audio file size is under 25MB

3. **Calendar events not showing**
   - Grant calendar permissions
   - Ensure you have at least one calendar account

4. **Files not accessible**
   - Check storage permissions
   - Verify Documents folder exists
   - Check available storage space

## Privacy & Security

- API keys are encrypted using AES256-GCM
- Audio files are stored locally on device
- No data is shared without explicit user action
- Calendar integration is optional

## Future Enhancements

- [ ] Multiple speaker identification
- [ ] Real-time transcription
- [ ] Cloud backup support
- [ ] Export to multiple formats (PDF, DOCX)
- [ ] Meeting analytics and insights
- [ ] Team collaboration features
- [ ] Custom AI prompts for summaries
- [ ] Integration with other calendar providers

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## Support

For issues or questions, please create an issue on GitHub or contact support@intelliswarm.ai 
