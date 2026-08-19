#!/usr/bin/env node
/**
 * test/smoke.js — automated smoke tests.
 *
 * Run with:  node test/smoke.js
 *
 * Tests:
 *  1. forge status — no crash, gives clear message about missing provider
 *  2. forge build --no-opencode — template mode, no LLM, no OpenCode required
 *  3. FORGE_EXECUTION_MODE=local — confirmed local path, E2B never touched
 *  4. FORGE_EXECUTION_MODE=e2b without key — clear error, no crash
 */
import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { rm } from 'node:fs/promises';

const ROOT = new URL('..', import.meta.url).pathname.replace(/\/$/, '');
const forge = join(ROOT, 'src', 'forge.js');

let passed = 0;
let failed = 0;

function run(args, env = {}) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [forge, ...args], {
      cwd: ROOT,
      shell: false,
      // Pipe stdout and stderr separately so JSON result stays clean
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, ...env },
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => (stdout += d));
    child.stderr.on('data', (d) => (stderr += d));
    child.on('close', (code) => resolve({ code, stdout, stderr }));
  });
}

function assert(label, condition, detail = '') {
  if (condition) {
    console.log(`  ✅ PASS: ${label}`);
    passed++;
  } else {
    console.error(`  ❌ FAIL: ${label}${detail ? ' — ' + detail : ''}`);
    failed++;
  }
}

console.log('\n=== Vexo Forge Smoke Tests ===\n');

// ── Test 1: status without any keys ──────────────────────────────────────────
console.log('Test 1: forge status — no API keys');
{
  const result = await run(['status'], {
    GEMINI_API_KEY: '',
    ANTHROPIC_API_KEY: '',
    OPENAI_API_KEY: '',
    FORGE_EXECUTION_MODE: 'local',
  });
  // Should exit 0 (status is informational, not a hard failure)
  assert('exit code 0', result.code === 0, `got ${result.code}`);
  // Should mention the missing provider — check both stdout (JSON) and stderr (log lines)
  const combined = result.stdout + result.stderr;
  assert('mentions no provider', combined.toLowerCase().includes('no') || combined.includes('provider') || combined.includes('warning'), combined.slice(0, 300));
}

// ── Test 2: build with --no-opencode (template mode, no LLM) ─────────────────
console.log('\nTest 2: forge build --no-opencode (template mode, no real API key needed)');
{
  // Clean up any stale test projects
  await rm(join(ROOT, '.forge-projects'), { recursive: true, force: true });

  const result = await run(
    ['build', '--prompt', 'Build a dental clinic test site', '--no-opencode'],
    {
      GEMINI_API_KEY: '',
      ANTHROPIC_API_KEY: '',
      OPENAI_API_KEY: '',
      FORGE_EXECUTION_MODE: 'local',
    }
  );

  assert('exits 0', result.code === 0, result.stderr.slice(0, 400));
  let parsed;
  try {
    parsed = JSON.parse(result.stdout.trim());
  } catch {
    parsed = null;
  }
  assert('returns JSON with ok:true', parsed?.ok === true, result.stdout.slice(0, 300));
  assert('mode is local', parsed?.mode === 'local', JSON.stringify(parsed));
  assert('previewUrl or previewHint present', !!(parsed?.previewHint || parsed?.previewUrl), JSON.stringify(parsed));
}

// ── Test 3: FORGE_EXECUTION_MODE=local never touches E2B ─────────────────────
console.log('\nTest 3: local mode — E2B never imported/called');
{
  // We patch by setting a clearly wrong E2B_API_KEY and confirming no E2B error surfaces
  const result = await run(
    ['build', '--prompt', 'Test site', '--no-opencode'],
    {
      FORGE_EXECUTION_MODE: 'local',
      E2B_API_KEY: '', // intentionally blank
    }
  );
  assert('exits 0', result.code === 0, result.stderr.slice(0, 300));
  const combined = result.stdout + result.stderr;
  assert('no E2B error', !combined.includes('E2B_API_KEY') && !combined.includes('e2b SDK'), combined.slice(0, 300));
}

// ── Test 4: E2B mode without key — clear error, no crash ─────────────────────
console.log('\nTest 4: FORGE_EXECUTION_MODE=e2b without E2B_API_KEY — graceful error');
{
  const result = await run(
    ['build', '--prompt', 'Test site', '--no-opencode'],
    {
      FORGE_EXECUTION_MODE: 'e2b',
      E2B_API_KEY: '',   // explicitly blank
    }
  );
  assert('exits non-zero (error expected)', result.code !== 0, `got code ${result.code}`);
  const combined = result.stdout + result.stderr;
  assert('mentions E2B_API_KEY', combined.includes('E2B_API_KEY'), combined.slice(0, 300));
  assert('mentions how to fix', combined.includes('local') || combined.includes('Set E2B_API_KEY'), combined.slice(0, 300));
}

// ─── Summary ──────────────────────────────────────────────────────────────────
console.log(`\n=== Results: ${passed} passed, ${failed} failed ===\n`);
process.exit(failed > 0 ? 1 : 0);
