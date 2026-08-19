#!/usr/bin/env node
/**
 * forge.js — main CLI entry point for the OpenCode-based Vexo Forge.
 *
 * Commands:
 *   forge init [--dir <path>]
 *   forge build --prompt "..." [--dir <path>]
 *   forge iterate --id <session-id> --prompt "..."
 *   forge deploy --id <session-id>
 *   forge status
 *
 * Environment variables:
 *   GEMINI_API_KEY        — for OpenCode Google/Gemini provider
 *   ANTHROPIC_API_KEY     — for OpenCode Anthropic/Claude provider
 *   OPENAI_API_KEY        — for OpenCode OpenAI provider
 *   FORGE_EXECUTION_MODE  — 'local' (default) or 'e2b'
 *   E2B_API_KEY           — required only when FORGE_EXECUTION_MODE=e2b
 *   VERCEL_TOKEN          — required only for the deploy command
 */
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile, readFile } from 'node:fs/promises';

import { logStep, requireEnv } from './logger.js';
import { requireOpencode, writeOpencodeConfig, runOpencode } from './opencode-bridge.js';
import { executeProject, getExecutionMode } from './executor.js';
import { resetDir, writeFiles, snapshotFiles } from './workspace.js';
import { createReactFiles } from './templates.js';

const root = process.cwd();
const sessionsDir = join(root, 'sessions');
const projectsDir = join(root, '.forge-projects');

// ─── CLI ARG HELPERS ─────────────────────────────────────────────────────────

function arg(name, fallback = '') {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] ?? fallback : fallback;
}

function flag(name) {
  return process.argv.includes(`--${name}`);
}

// ─── SESSION PERSISTENCE ──────────────────────────────────────────────────────

async function saveSession(session) {
  await mkdir(sessionsDir, { recursive: true });
  await writeFile(join(sessionsDir, `${session.id}.json`), JSON.stringify(session, null, 2));
}

async function loadSession(id) {
  const path = join(sessionsDir, `${id}.json`);
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch {
    throw new Error(`Session not found: ${id}\nLook for session IDs in the sessions/ directory.`);
  }
}

// ─── COMMANDS ────────────────────────────────────────────────────────────────

/**
 * forge init — write opencode.json in cwd (or --dir) so OpenCode is configured.
 */
async function cmdInit() {
  const dir = arg('dir', root);
  await writeOpencodeConfig(dir);
  console.log(JSON.stringify({ ok: true, action: 'init', configDir: dir, executionMode: getExecutionMode() }, null, 2));
}

/**
 * forge status — print current config (no API calls).
 */
async function cmdStatus() {
  const hasGemini = !!process.env.GEMINI_API_KEY;
  const hasAnthropic = !!process.env.ANTHROPIC_API_KEY;
  const hasOpenAI = !!process.env.OPENAI_API_KEY;
  const hasE2B = !!process.env.E2B_API_KEY;
  const mode = getExecutionMode();

  if (!hasGemini && !hasAnthropic && !hasOpenAI) {
    console.error('No LLM API key configured. Set one of: GEMINI_API_KEY, ANTHROPIC_API_KEY, OPENAI_API_KEY');
    console.error('Forge will still initialise but OpenCode will prompt for credentials when you run a build.');
  }

  console.log(JSON.stringify({
    ok: true,
    providers: { gemini: hasGemini, anthropic: hasAnthropic, openai: hasOpenAI },
    executionMode: mode,
    e2bKeyPresent: hasE2B,
    warning: (!hasGemini && !hasAnthropic && !hasOpenAI)
      ? 'No provider API key found — OpenCode will prompt interactively'
      : undefined,
  }, null, 2));
}

/**
 * forge build --prompt "..." [--dir projectDir] [--no-opencode]
 *
 * --no-opencode: skip OpenCode, use the built-in static template generator instead.
 *               Useful for smoke-testing without an LLM API key.
 */
async function cmdBuild() {
  const prompt = arg('prompt', 'Build a one-page dental clinic in Croydon, dark blue colors, booking CTA');
  const useOpencode = !flag('no-opencode');

  const id = randomUUID().slice(0, 8);
  const dir = arg('dir', join(projectsDir, id));

  await mkdir(dir, { recursive: true });
  logStep('build', `id=${id} mode=${getExecutionMode()} opencode=${useOpencode}`);

  if (useOpencode) {
    // ── OpenCode path ──────────────────────────────────────────────────────
    await requireOpencode();
    await writeOpencodeConfig(dir);

    // Scaffold a minimal package.json so OpenCode has something to work with
    const scaffold = createReactFiles(prompt);
    await writeFiles(dir, scaffold);

    // Hand off to OpenCode for LLM-driven generation/editing
    const ocResult = await runOpencode(
      `You are building a production-quality web app. ${prompt}. ` +
      `Generate all necessary files in the current directory. ` +
      `Ensure package.json has a "build" script and a "dev" script.`,
      dir
    );

    if (ocResult.code !== 0) {
      throw new Error(`OpenCode failed (exit ${ocResult.code}): ${ocResult.stderr || ocResult.stdout}`);
    }
  } else {
    // ── Template path (no LLM, smoke-test only) ────────────────────────────
    logStep('build:template', 'using built-in template (--no-opencode flag set)');
    const files = createReactFiles(prompt);
    await writeFiles(dir, files);
  }

  // Execute (build + optional E2B upload)
  const execResult = await executeProject(dir);

  if (execResult.code !== 0) {
    throw new Error(`Build failed: ${execResult.error}`);
  }

  const session = {
    id,
    prompt,
    dir,
    mode: execResult.mode,
    previewUrl: execResult.previewUrl,
    previewHint: execResult.previewHint,
    opencode: useOpencode,
    promptHistory: [prompt],
    createdAt: new Date().toISOString(),
  };
  await saveSession(session);

  console.log(JSON.stringify({ ok: true, projectId: id, ...execResult }, null, 2));
}

/**
 * forge iterate --id <id> --prompt "change..."
 */
async function cmdIterate() {
  const id = arg('id');
  const prompt = arg('prompt');
  if (!id || !prompt) throw new Error('Usage: forge iterate --id <session-id> --prompt "change..."');

  const session = await loadSession(id);
  const useOpencode = !flag('no-opencode') && session.opencode !== false;

  if (useOpencode) {
    await requireOpencode();
    await writeOpencodeConfig(session.dir);
    const ocResult = await runOpencode(
      `Apply this change to the existing web app: ${prompt}`,
      session.dir
    );
    if (ocResult.code !== 0) {
      throw new Error(`OpenCode failed: ${ocResult.stderr || ocResult.stdout}`);
    }
  } else {
    // Template-only iteration: patch CSS as a minimal example
    const files = await snapshotFiles(session.dir);
    if (files['src/style.css'] !== undefined) {
      files['src/style.css'] += `\n/* Iteration: ${prompt.replaceAll('*/', '')} */\n.button,button{filter:saturate(1.2);}\n`;
      await writeFiles(session.dir, { 'src/style.css': files['src/style.css'] });
    }
  }

  const execResult = await executeProject(session.dir);
  if (execResult.code !== 0) throw new Error(`Rebuild failed: ${execResult.error}`);

  session.promptHistory.push(prompt);
  session.updatedAt = new Date().toISOString();
  session.previewUrl = execResult.previewUrl ?? session.previewUrl;
  await saveSession(session);

  console.log(JSON.stringify({ ok: true, projectId: id, updated: true, ...execResult }, null, 2));
}

/**
 * forge deploy --id <id>
 */
async function cmdDeploy() {
  const id = arg('id');
  if (!id) throw new Error('Usage: forge deploy --id <session-id>');
  requireEnv('VERCEL_TOKEN', 'human-approved Vercel deployment');
  throw new Error(
    'Vercel deploy gate reached: token is present, but direct upload is intentionally left as ' +
    'an operator-approved integration step. See RESEARCH_NOTES.md.'
  );
}

// ─── ENTRYPOINT ───────────────────────────────────────────────────────────────

const command = process.argv[2] || 'help';

try {
  if      (command === 'init')    await cmdInit();
  else if (command === 'build')   await cmdBuild();
  else if (command === 'status')  await cmdStatus();
  else if (command === 'iterate') await cmdIterate();
  else if (command === 'deploy')  await cmdDeploy();
  else {
    console.log([
      'Vexo Forge — OpenCode-powered web app generator',
      '',
      'Usage:',
      '  forge init              — write opencode.json config for current provider',
      '  forge status            — show current config (no API calls)',
      '  forge build [options]   — generate and build a web app',
      '    --prompt "..."          prompt (required)',
      '    --no-opencode           use built-in template, skip OpenCode (smoke test)',
      '  forge iterate [options] — apply changes to an existing project',
      '    --id <session-id>       required',
      '    --prompt "..."          change description (required)',
      '  forge deploy            — deploy via Vercel (human-gated)',
      '    --id <session-id>       required',
      '',
      'Environment variables:',
      '  GEMINI_API_KEY          LLM provider (Google Gemini)',
      '  ANTHROPIC_API_KEY       LLM provider (Anthropic Claude)',
      '  OPENAI_API_KEY          LLM provider (OpenAI)',
      '  FORGE_EXECUTION_MODE    "local" (default) or "e2b"',
      '  E2B_API_KEY             Required only when FORGE_EXECUTION_MODE=e2b',
      '  VERCEL_TOKEN            Required only for forge deploy',
    ].join('\n'));
  }
} catch (error) {
  console.error(`\nERROR: ${error.message}`);
  process.exit(1);
}
