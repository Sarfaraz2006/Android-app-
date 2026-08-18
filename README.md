# Vexo Forge

Vexo Forge is an internal, CLI-first AI app-builder prototype for Vexo TeamX. It accepts a marketing-site prompt, creates a static app project, builds it, records a session, supports a basic iteration command, and gates production deployment.

## Requirements

- Ubuntu/Hetzner VPS or any SSH shell
- Node.js 20+
- Network access for `npm install`
- Optional real providers:
  - Install `@anthropic-ai/sdk` yourself and set `ANTHROPIC_API_KEY` for Claude code generation with `FORGE_CODE_PROVIDER=anthropic`
  - Install `e2b` yourself and set `E2B_API_KEY` for future E2B sandbox execution with `FORGE_SANDBOX_PROVIDER=e2b`
  - `VERCEL_TOKEN` for the human-gated deploy step

## Install

```bash
npm install
```

The committed prototype has no mandatory runtime dependencies. Real Claude/E2B providers are optional so the SSH-only smoke tests can run even when registry policy blocks paid SDK packages.

## Phase commands

### Phase 1 — sandbox smoke test

```bash
npm run phase1
```

Creates a local sandbox project under `.forge-projects/`, installs dependencies, runs `npm run build`, and prints JSON status.

### Phase 2 — single-shot app generation

```bash
npm run phase2
```

Or provide your own prompt:

```bash
npm run forge -- build --prompt "Build a one-page dental clinic in Croydon, booking CTA, gallery, contact form, dark blue colors"
```

The command prints a `projectId` and a preview command. Run the preview from SSH:

```bash
cd .forge-projects/<projectId>
npm run dev -- --host 0.0.0.0
```

Then open `http://<server-ip>:5173` from your phone if the VPS firewall allows the port.

### Phase 3 — error-correction loop

```bash
npm run phase3
```

This intentionally breaks a stylesheet import, confirms the build fails, regenerates a clean app, and proves recovery.

### Phase 4 — iteration support

```bash
npm run forge -- iterate --id <projectId> --prompt "make the CTA feel more premium"
```

### Phase 5 — deploy gate

```bash
VERCEL_TOKEN=your_real_token npm run forge -- deploy --id <projectId>
```

Deployment is intentionally human-gated. The command verifies the secret exists and then stops with a clear message until the Vercel direct-upload implementation is completed by the operator-approved step.

## Provider switching

Default mode uses deterministic local templates, so it works without API keys. To use Claude after adding a real key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export FORGE_CODE_PROVIDER=anthropic
export CLAUDE_MODEL=claude-sonnet-5
npm run forge -- build --prompt "Build a one-page salon site in Manchester with pink branding"
```

## Notes

- No placeholder secrets are included.
- Session metadata lives in `sessions/<projectId>.json`.
- Generated project workspaces live in `.forge-projects/` and are gitignored.
- Research findings are in `RESEARCH_NOTES.md`.
