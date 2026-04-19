# Aria Android App (Native Kotlin)

This repo now contains a **native Android (Jetpack Compose + Kotlin)** AI assistant app named **Aria**.

## What this app includes

- ChatGPT-style conversational UI
- Text input + voice input (speech-to-text)
- Spoken replies (text-to-speech)
- Local chat history persistence (Room)
- In-app **Settings** screen with only required fields:
  - API key
  - Model dropdown
- Uses **OpenRouter chat-completions** with fixed endpoint + configurable API key/model.

## Build debug APK locally

```bash
gradle assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk` (CI also publishes unique filename artifacts: `aria-studio-debug-<run_number>.apk`)

## Build release APK locally

```bash
gradle assembleRelease
```

Output:

`app/build/outputs/apk/release/app-release.apk`

## GitHub Actions

Workflow: `.github/workflows/android.yml`

- Builds debug APK on push / PR
- Uploads uniquely named APK artifact per run
- On tags like `v1.0.0`, builds + uploads release APK artifact

## Notes

- Add your OpenRouter API key from app Settings before first chat.
- Endpoint is fixed to `https://openrouter.ai/api/v1/chat/completions` for stable behavior.


> Note: This repository currently does not include `gradle-wrapper.jar`, so use `gradle` in CI/local until wrapper is restored.
