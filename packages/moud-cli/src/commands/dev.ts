import { Command } from 'commander';
import { logger } from '../services/logger.js';
import { EnvironmentManager } from '../services/environment.js';
import { Transpiler } from '../services/transpiler.js';
import { spawn, ChildProcess } from 'child_process';
import chokidar from 'chokidar';
import path from 'path';

let serverProcess: ChildProcess | null = null;
let isRestarting = false;

async function startServer(environment: EnvironmentManager, transpiler: Transpiler) {
    logger.step('Transpiling scripts...');
    await transpiler.transpileProject();
    logger.success('Scripts transpiled successfully.');

    const projectRoot = process.cwd();
    const javaExecutable = environment.getJavaExecutablePath();
    const serverJar = environment.getServerJarPath();

    if (!javaExecutable || !serverJar) {
        logger.error('Environment is not correctly configured. Cannot start server.');
        process.exit(1);
    }

    logger.step('Starting Moud Server...');

    serverProcess = spawn(javaExecutable, [
        '-jar', serverJar,
        '--project-root', projectRoot
    ], { stdio: 'pipe' });

    serverProcess.stdout?.on('data', (data) => {
        process.stdout.write(`[Moud Engine] ${data.toString()}`);
    });

    serverProcess.stderr?.on('data', (data) => {
        process.stderr.write(`[Moud Engine] ${data.toString()}`);
    });

    serverProcess.on('close', (code) => {
        if (!isRestarting) {
            logger.warn(`Moud Server process exited with code ${code}.`);
        }
    });
}

async function restartServer(environment: EnvironmentManager, transpiler: Transpiler) {
    if (isRestarting) return;
    isRestarting = true;
    logger.info('Restarting Moud Server...');

    if (serverProcess) {
        serverProcess.kill('SIGINT');
        await new Promise(resolve => serverProcess?.on('close', resolve));
        serverProcess = null;
    }

    await startServer(environment, transpiler);
    isRestarting = false;
    logger.success('Server restarted.');
}

export const devCommand = new Command('dev')
  .description('Start the development server with hot-reloading')
  .action(async () => {
    logger.info('Starting Moud in development mode...');

    const environment = new EnvironmentManager();
    const transpiler = new Transpiler();

    await environment.initialize();
    await startServer(environment, transpiler);

    const watcher = chokidar.watch(['src/**/*', 'client/**/*', 'assets/**/*'], {
        ignored: /(^|[\/\\])\../,
        persistent: true,
    });

    logger.info('Watching for file changes...');

    watcher.on('change', async (filePath) => {
        logger.info(`File changed detected: ${path.basename(filePath)}`);
        await restartServer(environment, transpiler);
    });

    process.on('SIGINT', () => {
        logger.info('Shutting down Moud Server...');
        if (serverProcess) {
            serverProcess.kill('SIGINT');
        }
        watcher.close();
        process.exit(0);
    });
  });