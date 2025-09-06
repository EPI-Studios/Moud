import { Command } from 'commander';
import chalk from 'chalk';
import { spawn } from 'child_process';
import chokidar from 'chokidar';
import path from 'path';

export const devCommand = new Command('dev')
  .description('Start the development server')
  .action(async () => {
    console.log(chalk.cyan('Starting Moud in development mode...'));

    // TODO: Make sure to be in a MOUD project

    const projectRoot = process.cwd();
    const moudRoot = path.resolve(projectRoot, '../..');

    const serverProcess = spawn(
        path.join(moudRoot, 'gradlew'),
        [':server:run'],
        {
            cwd: moudRoot,
            stdio: 'pipe'
        }
    );

    serverProcess.stdout.on('data', (data) => {
      console.log(`${chalk.gray('[SERVER]')} ${data.toString().trim()}`);
    });

    serverProcess.stderr.on('data', (data) => {
      console.error(`${chalk.red('[SERVER-ERROR]')} ${data.toString().trim()}`);
    });

    const watcher = chokidar.watch([
        path.join(projectRoot, 'src/**/*.ts'),
        path.join(projectRoot, 'client/**/*.ts')
    ], { persistent: true });

    console.log(chalk.yellow('Watching for file changes...'));

    watcher.on('change', (filePath) => {
      console.log(chalk.green(`File changed: ${path.basename(filePath)}`));
      // TODO: Trigger hot-reloading to the java part
      console.log(chalk.blue('Triggering hot-reload... (not yet implemented)'));
    });

    process.on('SIGINT', () => {
        console.log(chalk.cyan('\nShutting down Moud development server...'));
        watcher.close();
        serverProcess.kill();
        process.exit(0);
    });
  });