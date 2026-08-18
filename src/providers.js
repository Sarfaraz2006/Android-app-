import { createReactFiles } from './templates.js';
import { requireEnv } from './logger.js';

function parseJsonResponse(text, provider) {
  const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/```$/i, '').trim();
  try {
    return JSON.parse(cleaned);
  } catch (error) {
    throw new Error(`${provider} returned non-JSON codegen output: ${error.message}`);
  }
}

export async function generateFiles(prompt, { provider = process.env.FORGE_CODE_PROVIDER || 'template' } = {}) {
  if (provider === 'template') return createReactFiles(prompt);
  if (provider === 'anthropic') {
    requireEnv('ANTHROPIC_API_KEY', 'Claude code generation');
    const Anthropic = (await import('@anthropic-ai/sdk')).default;
    const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
    const response = await client.messages.create({ model: process.env.CLAUDE_MODEL || 'claude-sonnet-5', max_tokens: 8000, messages: [{ role: 'user', content: `Return only JSON mapping file paths to contents for a Vite React app. Prompt: ${prompt}` }] });
    const text = response.content.filter((part) => part.type === 'text').map((part) => part.text).join('\n');
    return parseJsonResponse(text, 'Anthropic');
  }
  if (provider === 'gemini') {
    requireEnv('GEMINI_API_KEY', 'Gemini code generation');
    const { GoogleGenAI } = await import('@google/genai');
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
    const response = await ai.models.generateContent({
      model: process.env.GEMINI_MODEL || 'gemini-2.5-flash',
      contents: `Return only JSON mapping file paths to contents for a dependency-light static web app. Prompt: ${prompt}`,
      config: {
        responseMimeType: 'application/json'
      }
    });
    return parseJsonResponse(response.text || '', 'Gemini');
  }
  throw new Error(`Unknown code provider: ${provider}`);
}

export async function maybeUseE2B() {
  if (process.env.FORGE_SANDBOX_PROVIDER !== 'e2b') return null;
  requireEnv('E2B_API_KEY', 'E2B sandbox execution');
  const Sandbox = (await import('e2b')).default;
  return Sandbox.create();
}
