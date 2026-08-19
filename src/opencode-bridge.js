/**
 * opencode-bridge.js
 * Thin subprocess wrapper around the `opencode` CLI.
 * Keeps Vexo Forge decoupled from OpenCode internals —
 * we call opencode as an external binary, not as a library.
 */
import { spawn } from 'node:child_process';
import { mkdir, writeFile, access } from 'node:fs/promises';
import { join } from 'node:path';
import { logStep } from './logger.js';

const OPENCODE_NOT_FOUND_MSG = `
ERROR: 'opencode' is not installed or not in PATH.

Install it for your environment:

  Ubuntu / VPS / standard Linux:
    npm install -g opencode-ai

  Termux on Android (no proot):
    curl -fsSL https://raw.githubusercontent.com/superman-enamy/opencode-termux-installer/main/install.sh | bash

  macOS (Homebrew):
    brew install anomalyco/tap/opencode

  Termux with proot-distro (most reliable):
    pkg install proot-distro && proot-distro install ubuntu
    proot-distro login ubuntu
    # then inside Ubuntu: npm install -g opencode-ai

After installing, re-run your forge command.
`.trim();

/**
 * Check whether the `opencode` binary exists in PATH.
 * @returns {Promise<boolean>}
 */
export async function isOpencodeAvailable() {
  return new Promise((resolve) => {
    const probe = spawn('opencode', ['--version'], { shell: false });
    probe.on('close', (code) => resolve(code === 0 || code === 1)); // some CLIs return 1 for --version
    probe.on('error', () => resolve(false));
  });
}

/**
 * Assert opencode is available, print a helpful message and exit if not.
 */
export async function requireOpencode() {
  const available = await isOpencodeAvailable();
  if (!available) {
    console.error(OPENCODE_NOT_FOUND_MSG);
    process.exit(1);
  }
}

/**
 * Write a minimal opencode.json into the given directory
 * based on which API keys are present in the environment.
 * This allows non-interactive scripted use of opencode.
 *
 * @param {string} dir - target directory (project root)
 */
export async function writeOpencodeConfig(dir) {
  await mkdir(dir, { recursive: true });

  const provider = {};
  let model = null;

  if (process.env.ANTHROPIC_API_KEY) {
    provider.anthropic = { options: { apiKey: '{env:ANTHROPIC_API_KEY}' } };
    model = model || 'anthropic/claude-sonnet-5';
  }
  if (process.env.GEMINI_API_KEY) {
    provider.google = { options: { apiKey: '{env:GEMINI_API_KEY}' } };
    model = model || 'google/gemini-2.5-flash';
  }
  if (process.env.OPENAI_API_KEY) {
    provider.openai = { options: { apiKey: '{env:OPENAI_API_KEY}' } };
    model = model || 'openai/gpt-4o';
  }

  if (!model) {
    // No provider keys found — write an empty config so opencode still initialises.
    // opencode will emit its own "no provider" error, which is what we want.
    logStep('warn', 'No LLM API key found in env (GEMINI_API_KEY, ANTHROPIC_API_KEY, OPENAI_API_KEY). OpenCode will prompt for credentials.');
  }

  const config = {
    $schema: 'https://opencode.ai/config.json',
    ...(Object.keys(provider).length > 0 && { provider }),
    ...(model && { model }),
    // Pre-grant bash/edit/read permissions so opencode doesn't block in non-interactive mode
    keybindings: {},
    autoapprove: { bash: true, edit: true, read: true },
  };

  const configPath = join(dir, 'opencode.json');
  await writeFile(configPath, JSON.stringify(config, null, 2));
  logStep('opencode-config', configPath);
  return configPath;
}

/**
 * Run opencode with a prompt in a given project directory.
 * opencode will generate/edit files directly on the filesystem.
 *
 * @param {string} prompt   - natural language instruction
 * @param {string} cwd      - project directory (opencode.json should already be here)
 * @param {object} opts
 * @param {boolean} opts.stream - stream stdout/stderr to parent process (default true)
 * @returns {Promise<{code: number, stdout: string, stderr: string}>}
 */
export async function runOpencode(prompt, cwd, { stream = true } = {}) {
  logStep('opencode', `running in ${cwd}`);

  return new Promise((resolve) => {
    // opencode run <prompt> in non-interactive mode
    const child = spawn('opencode', ['run', prompt], {
      cwd,
      shell: false,
      env: { ...process.env },
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (data) => {
      stdout += data;
      if (stream) process.stdout.write(data);
    });
    child.stderr.on('data', (data) => {
      stderr += data;
      if (stream) process.stderr.write(data);
    });
    child.on('close', (code) => resolve({ code: code ?? 1, stdout, stderr }));
    child.on('error', (err) => resolve({ code: 1, stdout, stderr: err.message }));
  });
}
