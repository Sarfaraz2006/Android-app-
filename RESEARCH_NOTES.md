# Vexo Forge — Research Notes

Last fully updated: **2026-08-19** (live-verified from docs and GitHub searches).

---

## Step 0 Research — OpenCode Architecture Pivot

### 1. OpenCode Install Methods (Verified)

**Package name:** `opencode-ai` (npm) — **not** `opencode` (that is a different, unrelated package).

| Method | Command | Works in Termux (no proot)? | Works on Ubuntu VPS? |
|--------|---------|----------------------------|---------------------|
| npm global | `npm install -g opencode-ai` | ❌ Binary misdetects Bionic libc | ✅ Clean |
| curl installer | `curl -fsSL https://opencode.ai/install \| bash` | ❌ Downloads standard Linux binary incompatible with Android Bionic | ✅ Clean |
| brew | `brew install anomalyco/tap/opencode` | ❌ Homebrew not available in base Termux | ✅ macOS/Linux |
| Community Termux script | `curl -fsSL https://raw.githubusercontent.com/superman-enamy/opencode-termux-installer/main/install.sh \| bash` | ✅ Pre-compiled native aarch64 binary | N/A |
| proot-distro Ubuntu inside Termux | `pkg install proot-distro && proot-distro install ubuntu` then standard npm inside | ✅ Full glibc env — most reliable | N/A |

**Design decision:**
- On Termux (no proot): use the community installer, documented in README.
- On Ubuntu VPS: `npm install -g opencode-ai`.
- Vexo Forge does NOT vendor or bundle OpenCode — it stays an external dependency.
- `forge` wrapper calls OpenCode as a subprocess. If `opencode` not in PATH it prints a clear install-it-first message and exits 1.

---

### 2. OpenCode Provider Configuration (Verified)

Auth stored in `~/.local/share/opencode/auth.json` after interactive `/connect`.

For non-interactive/scripted use, providers are configured in `opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "anthropic": {
      "options": { "apiKey": "{env:ANTHROPIC_API_KEY}" }
    },
    "google": {
      "options": { "apiKey": "{env:GEMINI_API_KEY}" }
    }
  },
  "model": "anthropic/claude-sonnet-5"
}
```

- `{env:VAR_NAME}` interpolation avoids hardcoded keys.
- Global config: `~/.config/opencode/opencode.json`
- Project config: `./opencode.json` (overrides global)
- Forge auto-writes a minimal `opencode.json` at `forge init` time based on which env vars are present.

---

### 3. OpenCode `opencode serve` — Headless Server Mode (Verified)

```bash
opencode serve [--port 4096] [--hostname 127.0.0.1]
```

- Starts HTTP server exposing an **OpenAPI 3.1 spec** on port 4096 by default.
- Auth via `OPENCODE_SERVER_PASSWORD` (HTTP Basic Auth). Username defaults to `opencode`.
- Official JS/TS SDK: `@opencode-ai/sdk` for programmatic control.
- Attach CLI to running server: `opencode run --attach http://localhost:4096 "Your prompt"`
- Logs: `~/.local/share/opencode/log/`
- Headless requires pre-granted tool permissions in `opencode.json` (`bash`, `edit`, `read`) or tasks block waiting for interactive approval.

**Forge usage:** Subprocess invocation for single-shot builds now. `opencode serve` reserved for future UI layer.

---

### 4. OpenCode Plugin System (Verified)

- Full TypeScript plugin API: `.opencode/plugins/` (project-level), `~/.config/opencode/plugins/` (global), or npm-listed in `opencode.json`.
- SDK: `@opencode-ai/plugin`
- Hooks available: `session.created`, `session.idle`, `message.updated`, `tool.execute.before`, `tool.execute.after`

**Decision:** We do NOT use the plugin system for sandbox execution in this iteration. Subprocess wrapping is simpler, more portable, and requires no TypeScript compilation. Will revisit for live-preview/deploy phase.

---

### 5. E2B Node.js SDK — Termux Compatibility (Verified)

| Environment | `npm install e2b` result |
|-------------|--------------------------|
| Ubuntu VPS / standard Linux | ✅ Installs cleanly — pure JS, no native compile |
| Termux (Bionic, no proot) | ⚠️ Conditional — no mandatory native compilation but Bionic differences may surface in sub-deps |
| proot-distro Ubuntu in Termux | ✅ Fully reliable |

**Architecture decision:** E2B is strictly opt-in via `FORGE_EXECUTION_MODE=e2b`. Uses dynamic `import()` so the module is never loaded in local mode. Missing `E2B_API_KEY` gives a clear error, never a crash.

---

## Previous Research (E2B, Claude, Gemini — from 2026-08-18)

### E2B
- Install: `npm i e2b` / `pip install e2b`
- Auth: `E2B_API_KEY=e2b_***`
- API: `import Sandbox from 'e2b'`, `await Sandbox.create()`, `sandbox.commands.run(...)`
- Code Interpreter: separate `@e2b/code-interpreter` SDK

### Anthropic Claude API
- Current models: `claude-sonnet-5`, `claude-opus-5`, `claude-haiku-4-5-20251001`
- Default in forge: `claude-sonnet-5`

### Google Gemini API
- Current SDK: `@google/genai` (`npm install @google/genai`)
- OpenCode provider key: `google`

---

## Architecture Diagram (Updated 2026-08-19)

```
User terminal (Termux / VPS / laptop)
        │
        ▼
forge CLI (this repo — thin wrapper, Node.js, no native deps beyond Node itself)
        │  reads env: GEMINI_API_KEY / ANTHROPIC_API_KEY / OPENAI_API_KEY
        │  reads env: FORGE_EXECUTION_MODE (local | e2b)
        ▼
OpenCode (external dep, user installs separately)
  Handles: LLM calls, file generation/editing, provider switching
  Configured via: opencode.json (auto-written by forge init)
        │
        ▼
Execution mode switch:
  LOCAL (default) — build/test runs via local child_process on current filesystem
                    works identically on Termux, VPS, laptop
  E2B   (opt-in)  — project uploaded to E2B cloud sandbox
                    returns preview URL
                    fails gracefully if E2B_API_KEY missing or SDK unavailable
        │
        ▼
(Future) Deploy step — Vercel API, human-gated
```
