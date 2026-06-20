# ScreenMate AI

Native Android prototype for a ChatGPT-style assistant that can chat, listen, speak, read the visible accessibility tree, and click accessible UI elements after the user enables Android Accessibility permission.

## Current capabilities

- Chat UI with assistant/user messages.
- Voice mode using Android `SpeechRecognizer` and `TextToSpeech`.
- Settings screen for provider switching:
  - Mock local assistant for development without keys.
  - Google Gemini REST API.
  - OpenRouter chat completions.
  - OpenAI-compatible chat completions.
- Accessibility service for screen context and click commands.
- GitHub Actions workflow that builds and uploads a debug APK artifact.

## Important Android limits

The app can read and click only what Android exposes through Accessibility APIs. It cannot bypass secure screens, hidden content, app restrictions, or protected system UI. Screen pixel capture would require a separate MediaProjection consent flow, which is not included in this first prototype.

## Build

GitHub Actions builds the debug APK automatically on pull requests and pushes to `main`.

For live AI calls, open Settings inside the app and choose a provider, model, endpoint if needed, and API key. Use `Mock` while developing UI and accessibility behavior.
