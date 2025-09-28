import { Command } from 'commander';
import { logger } from '../services/logger.js';
import { EnvironmentManager } from '../services/environment.js';
import { Transpiler } from '../services/transpiler.js';
import { HotReloadManager } from '../services/hot-reload-manager.js';
import { CleanupManager } from '../services/cleanup-manager.js';
import { spawn, ChildProcess } from 'child_process';
import chokidar from 'chokidar';
import chalk from 'chalk';
import path from 'path';

interface DevOptions {
  port: string;
  onlineMode: string;
  watch: boolean;
}

export class DevServer {
  private serverProcess: ChildProcess | null = null;
  private isShuttingDown = false;
  private cleanupManager: CleanupManager;
  private hotReloadManager: HotReloadManager;

  constructor(private environment: EnvironmentManager, private transpiler: Transpiler) {
    this.cleanupManager = new CleanupManager();
    this.hotReloadManager = new HotReloadManager(transpiler);
    this.setupGracefulShutdown();
  }

  private setupGracefulShutdown(): void {
    process.on('SIGINT', () => this.shutdown());
    process.on('SIGTERM', () => this.shutdown());
  }

  async startServer(options: DevOptions): Promise<void> {
    if (this.isShuttingDown) return;

    try {
      logger.step('Transpiling scripts...');
      await this.transpiler.transpileProject();
      logger.success('Scripts transpiled successfully.');

      const projectRoot = process.cwd();
      const javaExecutable = this.environment.getJavaExecutablePath();
      const serverJar = this.environment.getServerJarPath();

      if (!javaExecutable || !serverJar) {
        throw new Error('Environment is not correctly configured. Cannot start server.');
      }

      logger.step(`Starting Moud Server on port ${options.port} (Online Mode: ${options.onlineMode})...`);

      const serverArgs = [
        '-jar', serverJar,
        '--project-root', projectRoot,
        '--port', options.port,
        '--online-mode', options.onlineMode,
        '--enable-reload'
      ];

      this.serverProcess = spawn(javaExecutable, serverArgs, {
        stdio: 'pipe',
        detached: false
      });

      if (this.serverProcess.pid) {
        this.cleanupManager.registerProcess(this.serverProcess.pid);
        this.hotReloadManager.setServerInfo(this.serverProcess, parseInt(options.port));
      }

      this.serverProcess.stdout?.on('data', (data) => {
        process.stdout.write(data.toString());
      });

      this.serverProcess.stderr?.on('data', (data) => {
        process.stderr.write(chalk.red(data.toString()));
      });

      this.serverProcess.on('error', (error) => {
        if (!this.isShuttingDown) {
          logger.error(`Server process error: ${error.message}`);
        }
      });

      this.serverProcess.on('close', (code, signal) => {
        if (!this.isShuttingDown) {
          logger.warn(`Moud Server process exited with code ${code} (signal: ${signal}).`);
        }
        this.serverProcess = null;
      });

      await new Promise<void>((resolve, reject) => {
        if (!this.serverProcess) {
          reject(new Error('Failed to start server process'));
          return;
        }

        const timeout = setTimeout(() => {
          reject(new Error('Server startup timeout'));
        }, 15000);

        this.serverProcess.once('spawn', () => {
          clearTimeout(timeout);
          resolve();
        });

        this.serverProcess.once('error', (error) => {
          clearTimeout(timeout);
          reject(error);
        });
      });

    } catch (error) {
      throw new Error(`Failed to start server: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  async reloadScripts(): Promise<void> {
    try {
      await this.hotReloadManager.reloadScripts();
    } catch (error) {
      logger.error('Hot reload failed, server may need manual restart');
      throw error;
    }
  }

  async stopServer(): Promise<void> {
    if (!this.serverProcess) return;

    return new Promise<void>((resolve) => {
      const cleanup = () => {
        this.serverProcess = null;
        resolve();
      };

      const forceKillTimeout = setTimeout(() => {
        if (this.serverProcess) {
          this.serverProcess.kill('SIGKILL');
          cleanup();
        }
      }, 5000);

      if (this.serverProcess) {
        this.serverProcess.once('close', () => {
          clearTimeout(forceKillTimeout);
          cleanup();
        });
        this.serverProcess.kill('SIGTERM');
      } else {
        clearTimeout(forceKillTimeout);
        cleanup();
      }
    });
  }

  async shutdown(): Promise<void> {
    if (this.isShuttingDown) return;

    this.isShuttingDown = true;
    logger.info('Shutting down Moud Server...');

    await this.stopServer();
    await this.cleanupManager.cleanup();

    process.exit(0);
  }
}

export const devCommand = new Command('dev')
  .description('Start the development server with hot reloading')
  .option('-p, --port <number>', 'Port to run the server on', '25565')
  .option('-o, --online-mode <boolean>', 'Enable Mojang authentication', 'false')
  .option('-w, --watch', 'Watch for file changes and hot reload scripts', true)
  .action(async (options: DevOptions) => {
    logger.info('Starting Moud in development mode...');

    try {
      const environment = new EnvironmentManager();
      const transpiler = new Transpiler();
      const devServer = new DevServer(environment, transpiler);

      await environment.initialize();
      await devServer.startServer(options);

      if (options.watch) {
        const watcher = chokidar.watch(['src/**/*', 'client/**/*'], {
          ignored: /(^|[\/\\])\../,
          persistent: true,
          ignoreInitial: true
        });

        logger.success('Hot reload enabled - watching for file changes...');

        watcher.on('change', async (filePath) => {
          logger.info(`File changed: ${path.basename(filePath)}`);
          try {
            await devServer.reloadScripts();
          } catch (error) {
            logger.warn('Hot reload failed - you may need to restart the server manually');
          }
        });

        watcher.on('error', (error) => {
          logger.error(`Watcher error: ${error.message}`);
        });
      } else {
        logger.info('Hot reload is disabled. Use --watch to enable automatic script reloading.');
      }

    } catch (error) {
      logger.error(`Failed to start development server: ${error instanceof Error ? error.message : String(error)}`);
      process.exit(1);
    }
  });