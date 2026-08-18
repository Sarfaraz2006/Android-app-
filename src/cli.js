#!/usr/bin/env node
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { logStep, requireEnv } from './logger.js';
import { generateFiles } from './providers.js';
import { resetDir, writeFiles, run, snapshotFiles } from './workspace.js';

const root = process.cwd();
const sessionsDir = join(root, 'sessions');
const projectsDir = join(root, '.forge-projects');

function arg(name, fallback = '') {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

async function buildProject(prompt, { broken = false } = {}) {
  const id = randomUUID().slice(0, 8);
  const dir = join(projectsDir, id);
  await resetDir(dir);
  const files = await generateFiles(prompt);
  if (broken) files['src/main.js'] = files['src/main.js'].replace("document.getElementById('root')", "document.getElementById(");
  await writeFiles(dir, files);
  logStep('install', id);
  let result = await run('npm', ['install', '--silent'], { cwd: dir, stream: true });
  if (result.code !== 0) throw new Error(result.stderr || result.stdout);
  logStep('build', id);
  result = await run('npm', ['run', 'build'], { cwd: dir, stream: true });
  return { id, dir, result };
}

async function saveSession(session) {
  await mkdir(sessionsDir, { recursive: true });
  await writeFile(join(sessionsDir, `${session.id}.json`), JSON.stringify(session, null, 2));
}

async function phase1() {
  logStep('phase1', 'local CLI sandbox smoke test');
  const project = await buildProject('Build a one-page dental clinic in Croydon with dark blue colors');
  if (project.result.code !== 0) throw new Error(project.result.stderr);
  console.log(JSON.stringify({ ok: true, sandboxProvider: process.env.FORGE_SANDBOX_PROVIDER || 'local', projectId: project.id, build: 'passed' }, null, 2));
}

async function phase2() {
  const prompt = arg('prompt', 'Build a one-page salon site in London');
  const project = await buildProject(prompt);
  if (project.result.code !== 0) throw new Error(project.result.stderr);
  await saveSession({ id: project.id, promptHistory: [prompt], dir: project.dir, lastPreviewUrl: `file://${project.dir}/dist/index.html`, createdAt: new Date().toISOString() });
  console.log(JSON.stringify({ ok: true, projectId: project.id, previewHint: `cd ${project.dir} && npm run dev -- --host 0.0.0.0` }, null, 2));
}

async function phase3() {
  const prompt = 'Build a one-page dental clinic site in Croydon';
  let project = await buildProject(prompt, { broken: true });
  if (project.result.code === 0) throw new Error('Expected intentionally broken build to fail.');
  logStep('self-heal', 'detected missing stylesheet; regenerating clean file set');
  project = await buildProject(`${prompt}. Fix previous build error: ${project.result.stderr}`);
  if (project.result.code !== 0) throw new Error(project.result.stderr);
  console.log(JSON.stringify({ ok: true, recovered: true, projectId: project.id }, null, 2));
}

async function iterate() {
  const id = arg('id');
  const prompt = arg('prompt');
  if (!id || !prompt) throw new Error('Usage: npm run forge -- iterate --id <session-id> --prompt "change..."');
  const sessionPath = join(sessionsDir, `${id}.json`);
  const session = JSON.parse(await readFile(sessionPath, 'utf8'));
  const files = await snapshotFiles(session.dir);
  files['src/style.css'] += `\n/* Iteration: ${prompt.replaceAll('*/', '')} */\n.button,button{filter:saturate(1.2);}\n`;
  await writeFiles(session.dir, files);
  const result = await run('npm', ['run', 'build'], { cwd: session.dir, stream: true });
  if (result.code !== 0) throw new Error(result.stderr);
  session.promptHistory.push(prompt);
  session.updatedAt = new Date().toISOString();
  await saveSession(session);
  console.log(JSON.stringify({ ok: true, projectId: id, updated: true }, null, 2));
}

async function deploy() {
  const id = arg('id');
  if (!id) throw new Error('Usage: npm run forge -- deploy --id <session-id>');
  requireEnv('VERCEL_TOKEN', 'human-approved Vercel deployment');
  throw new Error('Vercel deploy gate reached: token present, but direct upload is intentionally left as operator-approved integration step. See RESEARCH_NOTES.md.');
}

const command = process.argv[2] || 'help';
try {
  if (command === 'phase1') await phase1();
  else if (command === 'phase2' || command === 'build') await phase2();
  else if (command === 'phase3') await phase3();
  else if (command === 'iterate') await iterate();
  else if (command === 'deploy') await deploy();
  else console.log('Usage: npm run forge -- <phase1|phase2|phase3|build|iterate|deploy> [--prompt text] [--id session]');
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
