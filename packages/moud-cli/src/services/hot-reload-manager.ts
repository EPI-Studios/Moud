import { logger } from './logger.js';
import { Transpiler } from './transpiler.js';
import { spawn, ChildProcess } from 'child_process';
import axios from 'axios';

interface ServerInfo {
  process: ChildProcess | null;
  port: number;
  pid?: number;
}

export class HotReloadManager {
  private serverInfo: ServerInfo = { process: null, port: 0 };
  private transpiler: Transpiler;
  private reloadEndpoint: string = '';

  constructor(transpiler: Transpiler) {
    this.transpiler = transpiler;
  }

  setServerInfo(process: ChildProcess, port: number) {
    this.serverInfo = { process, port };
    this.reloadEndpoint = `http://localhost:${port + 1000}/moud/api/reload`;
  }

  async reloadScripts(): Promise<void> {
    if (!this.serverInfo.process || !this.reloadEndpoint) {
      throw new Error('Server not running or reload endpoint not set');
    }

    try {
      logger.info('Hot reloading scripts...');

      await this.transpiler.transpileProject();
      logger.success('Scripts transpiled successfully');

      const response = await axios.post(this.reloadEndpoint, {
        action: 'reload_scripts'
      }, {
        timeout: 5000,
        headers: { 'Content-Type': 'application/json' }
      });

      if (response.status === 200) {
        logger.success('Scripts reloaded successfully');
      } else {
        throw new Error(`Server responded with status ${response.status}`);
      }

    } catch (error) {
      if (axios.isAxiosError(error) && error.code === 'ECONNREFUSED') {
        logger.warn('Server not responding to reload request, attempting full restart...');
        throw new Error('Hot reload failed - server not responding');
      } else {
        logger.error(`Hot reload failed: ${error instanceof Error ? error.message : String(error)}`);
        throw error;
      }
    }
  }

  isServerRunning(): boolean {
    return this.serverInfo.process !== null && !this.serverInfo.process.killed;
  }

  getServerPort(): number {
    return this.serverInfo.port;
  }
}