# Vexo Forge research notes

Verified/updated on 2026-08-18 from live documentation/search results.

## Environment constraints

Target runtime is a Hetzner CX23 VPS with Ubuntu and SSH-only access from Termius/Termux. Vexo Forge is therefore CLI-first, logs all results to stdout/stderr, stores sessions as JSON, and does not require a GUI browser for core checks.

## E2B

- Current JavaScript install command verified from the E2B repository README: `npm i e2b`.
- Current Python install command verified: `pip install e2b`.
- Auth is by `E2B_API_KEY=e2b_***` environment variable.
- Basic Node API shown by E2B is `import Sandbox from 'e2b'`, `await Sandbox.create()`, then `sandbox.commands.run(...)`.
- Code Interpreter remains a separate SDK (`@e2b/code-interpreter`) for `runCode()` style execution.
- Free-tier quota and exact timeout limits could not be verified from public indexed docs in this session; the implementation defaults to a local filesystem sandbox and only attempts E2B when `FORGE_SANDBOX_PROVIDER=e2b` and `E2B_API_KEY` are set.

Sources:
- https://github.com/e2b-dev/e2b
- https://e2b.dev/docs

## Anthropic Claude API

- Anthropic's model overview lists current Claude API IDs including `claude-fable-5`, `claude-opus-5`, `claude-sonnet-5`, and `claude-haiku-4-5-20251001`.
- The docs recommend Opus 5 for complex agentic coding and Sonnet 5 as a speed/intelligence balance.
- This repo defaults `CLAUDE_MODEL` to `claude-sonnet-5` when `FORGE_CODE_PROVIDER=anthropic` is selected.
- The live implementation requires `ANTHROPIC_API_KEY` and uses the official `@anthropic-ai/sdk` package. No placeholder key is shipped.
- Pricing/rate-limit comfort for a 3-4 retry loop could not be verified without account-specific console limits; treat this as an operator check before enabling real Claude calls.

Sources:
- https://platform.claude.com/docs/en/about-claude/models/overview
- https://platform.claude.com/docs/en/api/messages

## Google Gemini API

- Current recommended JavaScript/TypeScript SDK is the Google Gen AI SDK package `@google/genai`, installed with `npm install @google/genai`; Google's docs mark the old `@google/generativeai` library as legacy/not actively maintained.
- Current standard content-generation REST endpoint is `POST https://generativelanguage.googleapis.com/v1beta/{model=models/*}:generateContent`; the model path format is `models/{model}`.
- Current request body requires `contents[]` for the conversation/current prompt, with optional `tools`, `toolConfig`, `safetySettings`, `systemInstruction`, and generation config fields.
- Current JavaScript SDK call shape is `const ai = new GoogleGenAI({}); await ai.models.generateContent({ model, contents })`; Vexo Forge passes `apiKey: process.env.GEMINI_API_KEY` explicitly and requests JSON output with `config.responseMimeType = 'application/json'`.
- The requested model ID `gemini-2.5-flash` is listed as a Gemini 2.5 Flash endpoint. Google currently lists newer Gemini 3.x Flash models too, but this build defaults to the user-requested `gemini-2.5-flash` and allows override with `GEMINI_MODEL`.
- Rate limits are measured by RPM, TPM, and RPD, apply per project rather than per API key, and RPD resets at midnight Pacific time. Google states that actual model limits depend on usage tier/account status and should be viewed in AI Studio; exact free-tier RPM/RPD numbers for this operator's project could not be verified without logging into AI Studio.

Sources:
- https://ai.google.dev/gemini-api/docs/libraries
- https://ai.google.dev/gemini-api/docs/generate-content/get-started
- https://ai.google.dev/api/generate-content
- https://ai.google.dev/gemini-api/docs/models
- https://ai.google.dev/gemini-api/docs/rate-limits

## Vercel REST API

- Vercel's REST API base is `https://api.vercel.com`.
- Vercel documents creating deployments with `POST` to the deployment endpoint.
- For non-Git deployments, Vercel says files are uploaded first through the file upload API and then referenced by the deployment request.
- Because deploy-to-production is human-gated in the PRD, the current CLI stops at the Vercel gate unless `VERCEL_TOKEN` is present, and still reports that operator approval/integration completion is needed.

Sources:
- https://vercel.com/docs/rest-api
- https://vercel.com/docs/rest-api/deployments/create-a-new-deployment
- https://vercel.com/docs/deployments

## Architecture decision for this build

The repository now contains a working local-first Vexo Forge prototype for Phases 1-4 and a guarded Phase 5 command. This avoids pretending that external paid/API-key services work in the current environment. Operators can switch from deterministic local template generation to Gemini/Claude/E2B by exporting the required environment variables.
