import { Command } from 'commander';
import inquirer from 'inquirer';
import { createProjectStructure } from '../services/scaffolder.js';
import { logger } from '../services/logger.js';

export const createCommand = new Command('create')
  .description('Create a new Moud project from a template')
  .action(async () => {
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'projectName',
        message: 'What is the name of your game?',
        validate: (input) => {
          if (/^([a-z0-9_-]+)$/.test(input)) return true;
          return 'Project name can only contain lowercase letters, numbers, hyphens, and underscores.';
        },
      },
      {
        type: 'list',
        name: 'template',
        message: 'Choose a project template:',
        choices: ['TypeScript (Default)'],
      },
    ]);

    try {
      await createProjectStructure(answers.projectName);
    } catch (error) {
      if (error instanceof Error) {
        logger.error(`Failed to create project: ${error.message}`);
      } else {
        logger.error('An unknown error occurred during project creation.');
      }
      process.exit(1);
    }
  });