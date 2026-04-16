# Aria Android App (Native Kotlin)

This repo now contains a **native Android (Jetpack Compose + Kotlin)** AI assistant app named **Aria**.

## What this app includes

- ChatGPT-style conversational UI
- Text input + voice input (speech-to-text)
- Spoken replies (text-to-speech)
- Local chat history persistence (Room)
- In-app **Settings** screen to configure your own provider:
  - Assistant name
  - Base URL
  - Endpoint path
  - Model name
  - API key
  - Temperature
  - System prompt
- Works with **OpenAI-compatible chat completion APIs** (OpenAI, OpenRouter, Groq-compatible gateways, Together-compatible endpoints, self-hosted proxies, etc.)

## Build debug APK locally

```bash
./gradlew assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk`

## Build release APK locally

```bash
./gradlew assembleRelease
```

Output:

`app/build/outputs/apk/release/app-release.apk`

## GitHub Actions

Workflow: `.github/workflows/android.yml`

- Builds debug APK on push / PR
- Uploads APK artifact
- On tags like `v1.0.0`, builds + uploads release APK artifact

## Notes

- Add API credentials from app Settings before first chat.
- This client expects an OpenAI-compatible JSON response schema for chat completions.
