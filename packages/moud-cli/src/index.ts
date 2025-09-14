#!/usr/bin/env node

import { Command } from 'commander';
import { createCommand } from './commands/create.js';
import { devCommand } from './commands/dev.js';
import { packCommand } from './commands/pack.js';

const program = new Command();

program
  .name('moud')
  .description('The official CLI for the Moud Project')
  .version('0.1.3');

program.addCommand(createCommand);
program.addCommand(devCommand);
program.addCommand(packCommand);

program.parse(process.argv);