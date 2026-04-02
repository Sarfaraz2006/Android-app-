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

## Phone-only flow (No PR merge required)

If you are working only from phone and cannot resolve PR conflicts:

1. Push code to any branch (or keep using `work`).
2. Open **Actions** tab.
3. Run workflow: **Manual APK Build (No PR Needed)**.
4. Select `debug` or `release`.
5. Download APK from workflow artifacts.

This path does not require creating or merging a pull request first.

## PR automation and merge safety

The CI workflow now does two automatic checks on each PR:

- **PR mergeability check**: fails if GitHub reports merge conflicts or blocked merge state.
- **APK build check**: builds and uploads `app-debug.apk` artifact automatically.

To make this fully automatic in GitHub:

1. Go to **Settings -> Branches -> Branch protection rules** for your main branch.
2. Mark these checks as required before merge:
   - `pr-merge-check`
   - `build`
3. (Optional) enable auto-merge in repository settings so PR merges immediately after checks pass.
