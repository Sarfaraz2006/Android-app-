# Voice Assistant Android App

Native Android starter app for a conversational voice assistant.

## What is implemented

- Push-to-talk voice input using Android `SpeechRecognizer`
- Partial transcript preview while listening
- AI response generation via chat-completions API
- Text-to-speech playback for assistant responses
- Optional typed text input + send button
- Local chat history persistence with Room
- Hilt-based dependency injection setup
- GitHub Actions workflow to build APK artifacts

## Configuration

Create `~/.gradle/gradle.properties` (or project `gradle.properties`) with:

```properties
API_BASE_URL=https://api.openai.com/
API_KEY=your_api_key_here
```

If `API_KEY` is blank, requests will fail until configured.

## Run locally

```bash
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

Workflow file: `.github/workflows/android.yml`

- Builds debug APK on push/PR
- Uploads debug APK as artifact
- On tags like `v1.0.0`, also builds and uploads release APK
- Uses `gradle` directly to avoid binary wrapper file issues in restricted PR systems

## Suggested next steps

1. Add unit/instrumentation tests for ViewModel, repository, and UI
2. Add streaming responses for lower perceived latency
3. Move API calls behind your own backend to avoid shipping API keys in app builds
4. Add wake-word support and tool integration flows
