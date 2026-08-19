# Vexo Forge

> **Agentic web-app generator powered by [OpenCode](https://opencode.ai) with optional [E2B](https://e2b.dev) cloud sandbox.**
> Internal CLI tool for Vexo TeamX. Accepts a plain-English marketing-site prompt → generates a full web app → builds and previews it — all from your terminal.

---

## Architecture

```
Your terminal (Termux / VPS / laptop / SSH session)
        │
        ▼
forge CLI  ← this repo — thin wrapper, no LLM logic
        │
        ▼
OpenCode  ← external dependency you install separately
           Does the actual LLM call + file generation/editing
           Supports: Gemini, Claude, OpenAI, and more
        │
        ▼
Execution mode (FORGE_EXECUTION_MODE):
  LOCAL (default) — build/test on local filesystem via npm
                    works on Termux, VPS, laptop — no extra dependencies
  E2B   (opt-in)  — upload to E2B cloud sandbox, get a preview URL
                    requires E2B_API_KEY; fails gracefully if missing
        │
        ▼
(Future) Deploy — Vercel API, human-gated
```

**Why OpenCode instead of custom provider code?**
OpenCode is an MIT-licensed, multi-provider agentic coding tool that already handles provider-switching (Gemini, Claude, OpenAI, etc.), prompt routing, and file editing. We use it as an engine so Vexo Forge can focus on the scaffolding, execution, and deploy layers rather than reimplementing LLM orchestration.

---

## Install Anywhere

The **same commands** work in Termux on Android, on a VPS over SSH, or on any Linux/Mac terminal.

### 1. Install Node.js (if not already present)

```bash
# Ubuntu / Debian / VPS
sudo apt install -y nodejs npm

# Termux on Android
pkg install nodejs

# macOS
brew install node
```

### 2. Clone and set up Vexo Forge

```bash
git clone https://github.com/Sarfaraz2006/Android-app-.git vexo-forge
cd vexo-forge
# No npm install needed — forge itself has zero mandatory runtime dependencies
```

### 3. Install OpenCode

OpenCode is the LLM engine. Install it separately:

```bash
# Ubuntu / standard Linux / VPS (recommended)
npm install -g opencode-ai

# macOS
brew install anomalyco/tap/opencode

# Termux on Android — use the community native installer (npm install fails in Termux without proot)
curl -fsSL https://raw.githubusercontent.com/superman-enamy/opencode-termux-installer/main/install.sh | bash

# Termux with proot-distro (most reliable option on Android)
pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu
# then inside Ubuntu:
npm install -g opencode-ai
```

> **Note:** The `forge build --no-opencode` flag uses a built-in template generator and does NOT require OpenCode. Use it to verify the rest of the stack works before installing OpenCode.

### 4. Set your LLM API key

At least one provider key is needed for full LLM-powered builds:

```bash
# Google Gemini (recommended)
export GEMINI_API_KEY="your-key-here"

# Anthropic Claude
export ANTHROPIC_API_KEY="your-key-here"

# OpenAI
export OPENAI_API_KEY="your-key-here"
```

Add to `~/.bashrc` or `~/.profile` for persistence.

---

## Quick Start

```bash
# Check your config
node src/forge.js status

# Smoke test — no API key or OpenCode needed
node src/forge.js build --prompt "dental clinic London" --no-opencode

# Full LLM build (requires OpenCode + API key)
node src/forge.js build --prompt "Build a one-page dental clinic in Croydon, dark blue, booking CTA"

# Iterate on an existing project
node src/forge.js iterate --id <session-id> --prompt "change the hero to be dark green"
```

---

## E2B Cloud Sandbox (Optional — Off by Default)

E2B is **entirely optional** and **off by default**. Local mode is the default and requires no additional dependencies beyond Node.js and OpenCode.

To enable E2B:

```bash
export FORGE_EXECUTION_MODE=e2b
export E2B_API_KEY="e2b_your_key_here"

node src/forge.js build --prompt "dental clinic site"
# → returns an e2b preview URL instead of a local file path
```

**What happens without E2B_API_KEY?**
```
ERROR: E2B mode is enabled (FORGE_EXECUTION_MODE=e2b) but E2B_API_KEY is not set.
Set E2B_API_KEY=e2b_*** to use E2B cloud sandboxes.
Or unset FORGE_EXECUTION_MODE (or set it to "local") to run builds locally.
```
No crash. Clear instructions. E2B code is never imported in local mode.

**E2B in Termux:** The e2b JS SDK has no mandatory native compilation step, but Termux's Bionic environment may surface compatibility issues in sub-dependencies. If you hit problems, use local mode (default) from Termux and E2B mode from your VPS.

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `GEMINI_API_KEY` | — | Google Gemini provider (for OpenCode) |
| `ANTHROPIC_API_KEY` | — | Anthropic Claude provider (for OpenCode) |
| `OPENAI_API_KEY` | — | OpenAI provider (for OpenCode) |
| `FORGE_EXECUTION_MODE` | `local` | `local` or `e2b` |
| `E2B_API_KEY` | — | Required only when `FORGE_EXECUTION_MODE=e2b` |
| `VERCEL_TOKEN` | — | Required only for `forge deploy` |

---

## Commands

```
forge init              Write opencode.json config (auto-detects your API keys)
forge status            Show current config — no API calls made
forge build [options]   Generate and build a web app
  --prompt "..."          Describe the site (required)
  --no-opencode           Use built-in template, skip OpenCode (smoke test)
forge iterate [options] Apply a change to an existing project
  --id <session-id>       Target session (required)
  --prompt "..."          What to change (required)
forge deploy            Deploy to Vercel (human-gated, requires VERCEL_TOKEN)
  --id <session-id>       Target session (required)
```

---

## Termux Verification

Run this to verify everything works in your Termux before doing a real build:

```bash
curl -fsSL https://raw.githubusercontent.com/Sarfaraz2006/Android-app-/main/termux-verify.sh | bash
```

Or clone first and run locally:

```bash
bash termux-verify.sh
```

---

## Legacy Phase Commands (Preserved)

The original prototype phase commands still work for backward compatibility:

```bash
npm run phase1    # local sandbox smoke test
npm run phase2    # single-shot build with prompt
npm run phase3    # intentional break + self-heal test
```

---

## File Structure

```
vexo-forge/
├── src/
│   ├── forge.js              ← main CLI (new — OpenCode-based)
│   ├── opencode-bridge.js    ← subprocess wrapper for OpenCode
│   ├── executor.js           ← execution mode switch (local / e2b)
│   ├── cli.js                ← legacy CLI (preserved)
│   ├── providers.js          ← legacy provider switching (archived, not deleted)
│   ├── templates.js          ← built-in static template generator
│   ├── workspace.js          ← filesystem + child_process helpers
│   └── logger.js             ← structured console logger
├── test/
│   └── smoke.js              ← automated smoke tests (node test/smoke.js)
├── termux-verify.sh          ← copy-pasteable Termux verification script
├── RESEARCH_NOTES.md         ← Step 0 research findings (2026-08-19)
└── README.md
```

---

## Why providers.js Is Still Here

`src/providers.js` is **not deleted** — it is **archived**. Reason: it covered Gemini + Claude + E2B for the old custom architecture. OpenCode now handles provider switching, but `providers.js` serves as a reference implementation and regression target until the team has confirmed full coverage. It will be cleaned up in a follow-up PR after a production build cycle with OpenCode.

---

## Requirements

- Node.js ≥ 18 (the only hard runtime dependency of Vexo Forge itself)
- OpenCode (`opencode-ai`, installed separately) — for LLM-powered builds
- One of: `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`, or `OPENAI_API_KEY` — for LLM calls
- `E2B_API_KEY` — optional, only needed for `FORGE_EXECUTION_MODE=e2b`
- `VERCEL_TOKEN` — optional, only needed for `forge deploy`
