# Vexo Forge research notes

Verified on 2026-08-17 from live documentation/search results.

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

The repository now contains a working local-first Vexo Forge prototype for Phases 1-4 and a guarded Phase 5 command. This avoids pretending that external paid/API-key services work in the current environment. Operators can switch from deterministic local template generation to Claude/E2B by exporting the required environment variables.
