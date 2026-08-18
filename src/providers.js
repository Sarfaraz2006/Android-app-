import { createReactFiles } from './templates.js';
import { requireEnv } from './logger.js';

export async function generateFiles(prompt, { provider = process.env.FORGE_CODE_PROVIDER || 'template' } = {}) {
  if (provider === 'template') return createReactFiles(prompt);
  if (provider === 'anthropic') {
    requireEnv('ANTHROPIC_API_KEY', 'Claude code generation');
    const Anthropic = (await import('@anthropic-ai/sdk')).default;
    const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
    const response = await client.messages.create({ model: process.env.CLAUDE_MODEL || 'claude-sonnet-5', max_tokens: 8000, messages: [{ role: 'user', content: `Return only JSON mapping file paths to contents for a Vite React app. Prompt: ${prompt}` }] });
    const text = response.content.filter((part) => part.type === 'text').map((part) => part.text).join('\n');
    return JSON.parse(text.replace(/^```json|```$/g, '').trim());
  }
  throw new Error(`Unknown code provider: ${provider}`);
}

export async function maybeUseE2B() {
  if (process.env.FORGE_SANDBOX_PROVIDER !== 'e2b') return null;
  requireEnv('E2B_API_KEY', 'E2B sandbox execution');
  const Sandbox = (await import('e2b')).default;
  return Sandbox.create();
}
