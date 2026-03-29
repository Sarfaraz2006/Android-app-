# Voice Assistant Android App

This repository now contains a native Android starter app focused on the first milestone: **a conversational voice assistant**.

## What is implemented

- Push-to-talk voice input (Android SpeechRecognizer)
- AI response generation via chat-completions API
- Text-to-speech playback for assistant responses
- Local chat history persistence with Room
- Clean architecture starter (UI + data + domain)
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
- Uses `gradle` directly (pinned to 8.7 in CI) to avoid binary wrapper file issues in restricted PR systems

## Next expansion phases

1. Streaming responses for more human-like latency
2. Wake-word support (on-device trigger)
3. Tool integration (email, WhatsApp Business API, social APIs)
4. Approval workflow for high-risk business actions
