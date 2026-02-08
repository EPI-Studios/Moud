#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const args = process.argv.slice(2);
const checkOnly = args.includes('--check');

const rootDir = resolve(new URL('..', import.meta.url).pathname);
const versionFile = resolve(rootDir, 'VERSION');
const versionedJsonFiles = [
  resolve(rootDir, 'package.json'),
  resolve(rootDir, 'packages', 'sdk', 'package.json'),
  resolve(rootDir, 'packages', 'moud-cli', 'package.json'),
  resolve(rootDir, 'client-mod', 'src', 'main', 'resources', 'fabric.mod.json'),
];

const version = (await readFile(versionFile, 'utf8')).trim();
if (!/^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/.test(version)) {
  console.error(`Invalid version in VERSION file: "${version}"`);
  process.exit(1);
}

let dirtyFiles = [];
for (const file of versionedJsonFiles) {
  const json = JSON.parse(await readFile(file, 'utf8'));
  if (json.version === version) {
    continue;
  }
  if (checkOnly) {
    dirtyFiles.push(file);
    continue;
  }
  json.version = version;
  await writeFile(file, JSON.stringify(json, null, 2) + '\n');
  console.log(`Updated ${file} -> ${version}`);
}

if (dirtyFiles.length > 0) {
  console.error('Version mismatch detected in:');
  dirtyFiles.forEach((file) => console.error(` - ${file}`));
  process.exit(1);
}
