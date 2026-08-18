export function logStep(label, details = '') {
  const suffix = details ? ` ${details}` : '';
  console.log(`[vexo-forge] ${label}${suffix}`);
}

export function requireEnv(name, purpose) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required for ${purpose}. Export it in your SSH shell; Vexo Forge will not invent placeholder secrets.`);
  }
  return value;
}
