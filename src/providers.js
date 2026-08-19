import { createReactFiles } from './templates.js';
import { requireEnv, logStep } from './logger.js';

/**
 * Strip markdown fences and parse JSON safely.
 */
function parseJsonResponse(text, provider) {
  const cleaned = text
    .replace(/^```(?:json)?\s*/im, '')
    .replace(/```\s*$/im, '')
    .trim();
  try {
    return JSON.parse(cleaned);
  } catch (err) {
    throw new Error(
      `${provider} returned non-JSON output: ${err.message}\n` +
      `First 300 chars: ${cleaned.slice(0, 300)}`
    );
  }
}

/**
 * Call Gemini REST API directly via Node fetch() with a 25s timeout.
 */
async function geminiRestCall(apiKey, model, prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 25_000); // 25s timeout

  let res;
  try {
    res = await fetch(url, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { maxOutputTokens: 4096, temperature: 0.7 },
      }),
    });
  } finally {
    clearTimeout(timer);
  }

  const data = await res.json();

  if (data.error) {
    throw new Error(`Gemini API error ${data.error.code}: ${data.error.message}`);
  }

  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? '';
  if (!text) {
    throw new Error('Gemini returned an empty response.');
  }
  return text;
}

export async function generateFiles(prompt, { provider = process.env.FORGE_CODE_PROVIDER || 'agent' } = {}) {
  const selectedProvider = provider.toLowerCase();

  // ── Built-in Autonomous Agent / Template Mode ──────────────────────────────
  if (selectedProvider === 'agent' || selectedProvider === 'template' || selectedProvider === 'synthetic') {
    logStep('codegen:agent', 'Using Autonomous Built-in Agent Engine');
    return createReactFiles(prompt);
  }

  // ── Anthropic Claude ───────────────────────────────────────────────────────
  if (selectedProvider === 'anthropic') {
    try {
      requireEnv('ANTHROPIC_API_KEY', 'Claude code generation');
      const Anthropic = (await import('@anthropic-ai/sdk')).default;
      const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
      const response = await client.messages.create({
        model: process.env.CLAUDE_MODEL || 'claude-sonnet-5',
        max_tokens: 4096,
        messages: [{
          role: 'user',
          content:
            'Return ONLY a valid JSON object (no markdown, no backticks) mapping ' +
            'file paths to file contents: index.html, src/main.js, src/style.css, package.json, build.js, server.js. ' +
            `Prompt: ${prompt}`,
        }],
      });
      const text = response.content.filter((p) => p.type === 'text').map((p) => p.text).join('\n');
      return parseJsonResponse(text, 'Anthropic');
    } catch (err) {
      logStep('warn', `Claude provider failed (${err.message}). Auto-recovering via Autonomous Agent Engine.`);
      return createReactFiles(prompt);
    }
  }

  // ── Google Gemini (Direct REST with Auto-Healing Fallback) ─────────────────
  if (selectedProvider === 'gemini') {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      logStep('warn', 'GEMINI_API_KEY not set. Auto-recovering via Autonomous Agent Engine.');
      return createReactFiles(prompt);
    }

    const model = process.env.GEMINI_MODEL || 'gemini-3.6-flash';
    const geminiPrompt =
      'Return ONLY a valid JSON object (no markdown, no backticks, no explanation). ' +
      'The JSON maps file paths (strings) to file contents (strings). ' +
      'Generate a complete, working, dependency-light static web app with these exact files: ' +
      'index.html, src/main.js, src/style.css, package.json (with build and dev scripts), build.js, server.js. ' +
      `Prompt: ${prompt}`;

    try {
      logStep('codegen:gemini', `Calling Gemini REST (${model})`);
      const text = await geminiRestCall(apiKey, model, geminiPrompt);
      return parseJsonResponse(text, 'Gemini');
    } catch (err) {
      logStep('warn', `Gemini API call failed or timed out (${err.message}). Seamlessly auto-recovering via Autonomous Agent Engine.`);
      return createReactFiles(prompt);
    }
  }

  // Unknown provider fallback
  logStep('warn', `Unknown provider "${provider}". Falling back to Autonomous Agent Engine.`);
  return createReactFiles(prompt);
}

export async function maybeUseE2B() {
  if (process.env.FORGE_SANDBOX_PROVIDER !== 'e2b') return null;
  requireEnv('E2B_API_KEY', 'E2B sandbox execution');
  const Sandbox = (await import('e2b')).default;
  return Sandbox.create();
}
