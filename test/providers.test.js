import test from 'node:test';
import assert from 'node:assert/strict';
import { generateFiles } from '../src/providers.js';

test('gemini provider requires GEMINI_API_KEY before SDK import', async () => {
  const previousKey = process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_API_KEY;
  await assert.rejects(
    () => generateFiles('Build a test site', { provider: 'gemini' }),
    /GEMINI_API_KEY is required for Gemini code generation/
  );
  if (previousKey) process.env.GEMINI_API_KEY = previousKey;
});
