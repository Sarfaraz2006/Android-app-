import test from 'node:test';
import assert from 'node:assert/strict';
import { createReactFiles, parsePrompt } from '../src/templates.js';

test('prompt parser extracts vertical and location', () => {
  assert.deepEqual(parsePrompt('Build dental clinic in Croydon with dark colors').vertical, 'dental clinic');
  assert.equal(parsePrompt('Build dental clinic in Croydon with dark colors').location, 'Croydon');
});

test('template emits vite react files', () => {
  const files = createReactFiles('Build a salon in London');
  assert.ok(files['package.json'].includes('build'));
  assert.ok(files['src/main.js'].includes('getElementById'));
});
