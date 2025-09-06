#!/usr/bin/env node

import { Command } from 'commander';
import { devCommand } from './commands/dev.js';
// import { buildCommand } from './commands/build.js';

const program = new Command();

program
  .name('moud')
  .description('The official CLI for the Moud Project')
  .version('0.1.0-alpha');

program.addCommand(devCommand);
// program.addCommand(buildCommand);

program.parse(process.argv);