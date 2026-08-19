import { mkdir, rm, writeFile, readFile, readdir, stat } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { spawn } from 'node:child_process';

export async function resetDir(dir) {
  await rm(dir, { recursive: true, force: true });
  await mkdir(dir, { recursive: true });
}

export async function writeFiles(root, files) {
  for (const [file, content] of Object.entries(files)) {
    const target = join(root, file);
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, content);
  }
}

export function run(command, args, options = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { cwd: options.cwd, shell: false, env: { ...process.env, ...options.env } });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (data) => { stdout += data; if (options.stream) process.stderr.write(data); });
    child.stderr.on('data', (data) => { stderr += data; if (options.stream) process.stderr.write(data); });
    child.on('close', (code) => resolve({ code, stdout, stderr }));
  });
}

export async function snapshotFiles(root, prefix = '') {
  const entries = await readdir(join(root, prefix));
  const files = {};
  for (const entry of entries) {
    if (entry === 'node_modules' || entry === 'dist') continue;
    const rel = prefix ? `${prefix}/${entry}` : entry;
    const info = await stat(join(root, rel));
    if (info.isDirectory()) Object.assign(files, await snapshotFiles(root, rel));
    else files[rel] = await readFile(join(root, rel), 'utf8');
  }
  return files;
}
