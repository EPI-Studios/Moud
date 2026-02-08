#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const rootDir = resolve(new URL('..', import.meta.url).pathname);
const versionFile = resolve(rootDir, 'VERSION');

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error('Usage: node scripts/bump-version.mjs <new-version|major|minor|patch>');
  process.exit(1);
}

const current = (await readFile(versionFile, 'utf8')).trim();
const targetArg = args[0];

const semverRegex = /^(\d+)\.(\d+)\.(\d+)(?:-[-0-9A-Za-z.]+)?$/;
const getParsed = (ver) => {
  const match = semverRegex.exec(ver);
  if (!match) return null;
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    suffix: match[4] ?? ''
  };
};

const increment = (type) => {
  const parsed = getParsed(current);
  if (!parsed) {
    console.error(`Current version '${current}' is not a valid semantic version.`);
    process.exit(1);
  }
  const { major, minor, patch } = parsed;
  switch (type) {
    case 'major':
      return `${major + 1}.0.0`;
    case 'minor':
      return `${major}.${minor + 1}.0`;
    case 'patch':
      return `${major}.${minor}.${patch + 1}`;
    default:
      console.error(`Unknown bump type '${type}'. Use major|minor|patch or provide an explicit version.`);
      process.exit(1);
  }
};

let nextVersion;
if (['major', 'minor', 'patch'].includes(targetArg)) {
  nextVersion = increment(targetArg);
} else if (semverRegex.test(targetArg)) {
  nextVersion = targetArg;
} else {
  console.error(`Invalid version '${targetArg}'. Provide semantic version (e.g. 0.8.1) or bump keyword.`);
  process.exit(1);
}

if (nextVersion === current) {
  console.log(`Version already ${current}, nothing to do.`);
  process.exit(0);
}

await writeFile(versionFile, `${nextVersion}\n`);
console.log(`Updated VERSION: ${current} -> ${nextVersion}`);

const sync = spawnSync('node', ['scripts/sync-version.mjs'], {
  cwd: rootDir,
  stdio: 'inherit'
});
if (sync.status !== 0) {
  console.error('Failed to propagate version to versioned manifests.');
  process.exit(sync.status ?? 1);
}

console.log(`Bumped project version to ${nextVersion}`);
