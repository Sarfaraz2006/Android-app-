/**
 * logger.js — minimal structured console logger.
 */
const start = Date.now();

export function logStep(step, detail = '') {
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);
  process.stderr.write(`[forge +${elapsed}s] ${step}${detail ? ': ' + detail : ''}\n`);
}

/**
 * Assert an environment variable is set, log a clear error and throw if not.
 * @param {string} name - env var name
 * @param {string} context - what it's needed for
 */
export function requireEnv(name, context) {
  if (!process.env[name]) {
    throw new Error(
      `${name} is required for ${context}. Set it in your shell or .env file before running forge.`
    );
  }
  return process.env[name];
}
