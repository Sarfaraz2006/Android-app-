import { createReactFiles } from './templates.js';
import { requireEnv, logStep } from './logger.js';

/**
 * Strip markdown fences and parse JSON safely.
 * Normalizes all file contents to strings.
 */
function parseJsonResponse(text, provider) {
  const cleaned = text
    .replace(/^```(?:json)?\s*/im, '')
    .replace(/```\s*$/im, '')
    .trim();

  let parsed;
  try {
    parsed = JSON.parse(cleaned);
  } catch (err) {
    throw new Error(
      `${provider} codegen output could not be parsed as JSON: ${err.message}\n` +
      `Raw output length: ${text.length} chars\n` +
      `First 300 chars: ${cleaned.slice(0, 300)}\n` +
      `Last 200 chars: ${cleaned.slice(-200)}`
    );
  }

  // Normalize: ensure every file value is a string
  const normalized = {};
  for (const [key, val] of Object.entries(parsed)) {
    normalized[key] = typeof val === 'string' ? val : JSON.stringify(val, null, 2);
  }
  return normalized;
}

/**
 * Call Gemini REST API directly via Node fetch().
 * Strict error handling — NO silent fallbacks.
 */
async function geminiRestCall(apiKey, model, prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 45_000); // 45s hard timeout

  let res;
  try {
    res = await fetch(url, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          maxOutputTokens: 8192,
          temperature: 0.7,
          responseMimeType: 'application/json',
        },
        safetySettings: [
          { category: 'HARM_CATEGORY_HARASSMENT', threshold: 'BLOCK_NONE' },
          { category: 'HARM_CATEGORY_HATE_SPEECH', threshold: 'BLOCK_NONE' },
          { category: 'HARM_CATEGORY_SEXUALLY_EXPLICIT', threshold: 'BLOCK_NONE' },
          { category: 'HARM_CATEGORY_DANGEROUS_CONTENT', threshold: 'BLOCK_NONE' },
        ],
      }),
    });
  } catch (fetchErr) {
    if (fetchErr.name === 'AbortError') {
      throw new Error(`Gemini API timed out after 45s (free-tier latency/rate limit).`);
    }
    throw new Error(`Gemini network connection failed: ${fetchErr.message}`);
  } finally {
    clearTimeout(timer);
  }

  const data = await res.json();

  if (data.error) {
    throw new Error(`Gemini API Error [${data.error.code}]: ${data.error.message}`);
  }

  const candidate = data?.candidates?.[0];
  const finishReason = candidate?.finishReason;

  if (finishReason === 'MAX_TOKENS') {
    throw new Error(
      `Gemini hit MAX_TOKENS limit (output was truncated mid-generation). Free-tier token limit reached for this single prompt.`
    );
  }

  if (finishReason === 'RECITATION' || finishReason === 'SAFETY') {
    throw new Error(`Gemini generation filtered by safety filter: ${finishReason} (${candidate?.finishMessage || ''})`);
  }

  const text = candidate?.content?.parts?.[0]?.text ?? '';
  if (!text) {
    throw new Error(`Gemini returned an empty response. Candidate status: ${JSON.stringify(candidate || {})}`);
  }
  return text;
}

export async function generateFiles(prompt, { provider = process.env.FORGE_CODE_PROVIDER || 'template' } = {}) {
  const selectedProvider = provider.toLowerCase();

  // ── 1. Built-in Template Engine (Explicit opt-in via FORGE_CODE_PROVIDER=template) ─
  if (selectedProvider === 'template') {
    logStep('codegen', 'Using built-in template engine (FORGE_CODE_PROVIDER=template)');
    return createReactFiles(prompt);
  }

  // ── 2. Anthropic Claude (Strict — NO silent fallback) ──────────────────────
  if (selectedProvider === 'anthropic') {
    requireEnv('ANTHROPIC_API_KEY', 'Claude code generation');
    const Anthropic = (await import('@anthropic-ai/sdk')).default;
    const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
    logStep('codegen:anthropic', 'Calling Claude API...');
    const response = await client.messages.create({
      model: process.env.CLAUDE_MODEL || 'claude-sonnet-5',
      max_tokens: 4096,
      messages: [{
        role: 'user',
        content:
          'Return ONLY a valid JSON object mapping file paths to file contents. ' +
          'Keys: "package.json", "index.html", "src/main.js", "src/style.css", "build.js", "server.js". ' +
          `Prompt: ${prompt}`,
      }],
    });
    const text = response.content.filter((p) => p.type === 'text').map((p) => p.text).join('\n');
    return parseJsonResponse(text, 'Anthropic');
  }

  // ── 3. Google Gemini (Strict — NO silent fallback) ────────────────────────
  if (selectedProvider === 'gemini') {
    requireEnv('GEMINI_API_KEY', 'Gemini code generation');
    const apiKey = process.env.GEMINI_API_KEY;
    // gemini-3.1-flash-lite is the proven fast, stable model with zero recitation filter issues
    const model = process.env.GEMINI_MODEL || 'gemini-3.1-flash-lite';

    const geminiPrompt =
      'Generate an original, bespoke single-page web application project. ' +
      'Return a valid JSON object mapping relative file paths to their exact file contents. ' +
      'Required files: ' +
      '1. "package.json" (must have "type": "module", and scripts: {"build": "node build.js", "dev": "node server.js"})\n' +
      '2. "index.html" (HTML5 document with <div id="root"></div> and script tag linking src/main.js)\n' +
      '3. "src/main.js" (vanilla JavaScript that dynamically builds and renders the complete landing page into #root)\n' +
      '4. "src/style.css" (modern responsive CSS styling)\n' +
      '5. "build.js" (ES module that creates dist/ directory, copies index.html to dist/index.html, and copies src/ to dist/src/)\n' +
      '6. "server.js" (simple Node.js HTTP server on port 5173)\n' +
      `Business Request: ${prompt}`;

    logStep('codegen:gemini', `Calling Gemini REST API (${model})...`);
    const text = await geminiRestCall(apiKey, model, geminiPrompt);
    return parseJsonResponse(text, 'Gemini');
  }

  throw new Error(`Unknown code provider: "${provider}". Supported: template, gemini, anthropic.`);
}

export async function maybeUseE2B() {
  if (process.env.FORGE_SANDBOX_PROVIDER !== 'e2b') return null;
  requireEnv('E2B_API_KEY', 'E2B sandbox execution');
  const Sandbox = (await import('e2b')).default;
  return Sandbox.create();
}
