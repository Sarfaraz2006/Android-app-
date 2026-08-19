/**
 * executor.js
 * Execution-mode switch: LOCAL (default) or E2B (opt-in cloud sandbox).
 *
 * LOCAL — build/test/preview via local child_process.
 *         Works identically on Termux, VPS, or any laptop.
 *         No external network beyond the LLM API call.
 *
 * E2B   — upload project to E2B cloud sandbox.
 *         Requires FORGE_EXECUTION_MODE=e2b AND E2B_API_KEY.
 *         Fails gracefully with a clear message if either is missing
 *         or if the e2b package cannot be loaded (e.g. Termux incompatibility).
 *         Never imported unless explicitly opted in.
 */
import { run } from './workspace.js';
import { logStep } from './logger.js';
import { join } from 'node:path';
import { readdir, readFile } from 'node:fs/promises';

/**
 * Current execution mode, driven by FORGE_EXECUTION_MODE env var.
 * Defaults to 'local'.
 * @returns {'local' | 'e2b'}
 */
export function getExecutionMode() {
  const raw = (process.env.FORGE_EXECUTION_MODE || 'local').toLowerCase().trim();
  if (raw === 'e2b') return 'e2b';
  return 'local';
}

/**
 * Build and optionally preview the project.
 * Routes to the correct executor based on FORGE_EXECUTION_MODE.
 *
 * @param {string} projectDir - absolute path to the generated project
 * @returns {Promise<{mode: string, code: number, previewUrl?: string, previewHint?: string, error?: string}>}
 */
export async function executeProject(projectDir) {
  const mode = getExecutionMode();

  if (mode === 'e2b') {
    return executeE2B(projectDir);
  }
  return executeLocal(projectDir);
}

// ─── LOCAL ───────────────────────────────────────────────────────────────────

async function executeLocal(projectDir) {
  logStep('execute:local', projectDir);

  // Install dependencies if package.json exists
  const installResult = await run('npm', ['install', '--silent'], { cwd: projectDir, stream: true });
  if (installResult.code !== 0) {
    return { mode: 'local', code: installResult.code, error: installResult.stderr || installResult.stdout };
  }

  // Build
  logStep('build:local', projectDir);
  const buildResult = await run('npm', ['run', 'build'], { cwd: projectDir, stream: true });

  return {
    mode: 'local',
    code: buildResult.code,
    previewUrl: `file://${join(projectDir, 'dist', 'index.html')}`,
    previewHint: `cd ${projectDir} && npm run dev -- --host 0.0.0.0`,
    error: buildResult.code !== 0 ? (buildResult.stderr || buildResult.stdout) : undefined,
  };
}

// ─── E2B ─────────────────────────────────────────────────────────────────────

async function executeE2B(projectDir) {
  logStep('execute:e2b', 'checking prerequisites');

  // 1. Check API key first — fail fast before even trying to load SDK
  const apiKey = process.env.E2B_API_KEY;
  if (!apiKey) {
    return {
      mode: 'e2b',
      code: 1,
      error: [
        'E2B mode is enabled (FORGE_EXECUTION_MODE=e2b) but E2B_API_KEY is not set.',
        'Set E2B_API_KEY=e2b_*** to use E2B cloud sandboxes.',
        'Or unset FORGE_EXECUTION_MODE (or set it to "local") to run builds locally.',
      ].join('\n'),
    };
  }

  // 2. Dynamically import e2b — fail gracefully if not installed or incompatible
  let Sandbox;
  try {
    const mod = await import('e2b');
    Sandbox = mod.default || mod.Sandbox;
  } catch (importErr) {
    return {
      mode: 'e2b',
      code: 1,
      error: [
        `Failed to load e2b SDK: ${importErr.message}`,
        '',
        'To install: npm install e2b',
        'Note: In Termux without proot-distro, the e2b SDK may not be compatible.',
        'In that case, run without FORGE_EXECUTION_MODE=e2b and use local mode instead.',
        'E2B execution works reliably from a standard Ubuntu VPS.',
      ].join('\n'),
    };
  }

  // 3. Run in E2B sandbox
  logStep('execute:e2b', 'creating sandbox');
  let sandbox;
  try {
    sandbox = await Sandbox.create({ apiKey });

    // Upload project files
    logStep('execute:e2b', 'uploading project files');
    await uploadDirToSandbox(sandbox, projectDir, '/home/user/project');

    // Run npm install + build inside sandbox
    logStep('execute:e2b', 'running npm install');
    const installOut = await sandbox.commands.run('cd /home/user/project && npm install --silent');
    if (installOut.exitCode !== 0) {
      await sandbox.close();
      return { mode: 'e2b', code: installOut.exitCode, error: installOut.stderr };
    }

    logStep('execute:e2b', 'running npm run build');
    const buildOut = await sandbox.commands.run('cd /home/user/project && npm run build');
    if (buildOut.exitCode !== 0) {
      await sandbox.close();
      return { mode: 'e2b', code: buildOut.exitCode, error: buildOut.stderr };
    }

    // Expose a preview port
    const port = 5173;
    await sandbox.commands.run(`cd /home/user/project && PORT=${port} npm run dev -- --host 0.0.0.0 &`);
    const previewUrl = `https://${sandbox.getHost(port)}`;

    logStep('execute:e2b', `preview at ${previewUrl}`);

    // Don't close sandbox — let it time out (E2B free tier ~5 min idle)
    return { mode: 'e2b', code: 0, previewUrl };
  } catch (runtimeErr) {
    if (sandbox) {
      try { await sandbox.close(); } catch (_) { /* ignore */ }
    }
    return {
      mode: 'e2b',
      code: 1,
      error: `E2B sandbox error: ${runtimeErr.message}`,
    };
  }
}

/**
 * Recursively upload a local directory to an E2B sandbox.
 * @param {object} sandbox
 * @param {string} localDir
 * @param {string} remotePath
 */
async function uploadDirToSandbox(sandbox, localDir, remotePath) {
  await sandbox.commands.run(`mkdir -p ${remotePath}`);
  const entries = await readdir(localDir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === '.git') continue;
    const localChild = join(localDir, entry.name);
    const remoteChild = `${remotePath}/${entry.name}`;
    if (entry.isDirectory()) {
      await uploadDirToSandbox(sandbox, localChild, remoteChild);
    } else {
      const content = await readFile(localChild, 'utf8');
      // Write file via echo — for large files, use sandbox.files.write if available
      const escaped = content.replace(/'/g, "'\\''");
      await sandbox.commands.run(`cat > '${remoteChild}' << 'FORGE_EOF'\n${content}\nFORGE_EOF`);
    }
  }
}
