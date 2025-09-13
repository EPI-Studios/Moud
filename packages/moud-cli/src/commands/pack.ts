import { Command } from 'commander';
import { logger } from '../services/logger.js';
import { Packer } from '../services/packer.js';

export const packCommand = new Command('pack')
  .description('Package the Moud project for distribution')
  .action(async () => {
    logger.info('Packaging Moud project for distribution...');
    const packer = new Packer();

    try {
        await packer.packProject();
    } catch (error) {
        if (error instanceof Error) {
            logger.error(`Failed to package project: ${error.message}`);
        } else {
            logger.error('An unknown error occurred during packaging.');
        }
        process.exit(1);
    }
  });