#!/usr/bin/env node

import { Command } from 'commander';
import { createCommand } from './commands/create.js';
import { devCommand } from './commands/dev.js';
import { packCommand } from './commands/pack.js';
import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const packageJson = JSON.parse(readFileSync(join(__dirname, '..', 'package.json'), 'utf-8'));

const program = new Command();

program
  .name('moud')
  .description('The official CLI for the Moud Project')
  .version(packageJson.version);

program.addCommand(createCommand);
program.addCommand(devCommand);
program.addCommand(packCommand);

program.parse(process.argv);