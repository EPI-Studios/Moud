import fs from 'fs';
import path from 'path';
import os from 'os';

export class CleanupManager {
  private tempDirs: Set<string> = new Set();
  private processes: Set<NodeJS.Process['pid']> = new Set();

  constructor() {
    process.on('SIGINT', () => this.cleanup());
    process.on('SIGTERM', () => this.cleanup());
    process.on('exit', () => this.cleanup());
    process.on('uncaughtException', (error) => {
      console.error('Uncaught exception:', error);
      this.cleanup();
      process.exit(1);
    });
  }

  registerTempDir(dir: string): void {
    this.tempDirs.add(dir);
  }

  registerProcess(pid: NodeJS.Process['pid']): void {
    if (pid) this.processes.add(pid);
  }

  async cleanup(): Promise<void> {
    await Promise.all([
      this.cleanupTempDirs(),
      this.cleanupProcesses()
    ]);
  }

  private async cleanupTempDirs(): Promise<void> {
    for (const dir of this.tempDirs) {
      try {
        if (fs.existsSync(dir)) {
          await fs.promises.rm(dir, { recursive: true, force: true });
        }
      } catch (error) {
        console.error(`Failed to cleanup temp dir ${dir}:`, error);
      }
    }
    this.tempDirs.clear();
  }

  private async cleanupProcesses(): Promise<void> {
    for (const pid of this.processes) {
      try {
        process.kill(pid, 'SIGTERM');
        setTimeout(() => {
          try {
            process.kill(pid, 'SIGKILL');
          } catch {}
        }, 5000);
      } catch (error) {
        console.error(`Failed to cleanup process ${pid}:`, error);
      }
    }
    this.processes.clear();
  }

  createTempDir(prefix: string = 'moud-'): string {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
    this.registerTempDir(tempDir);
    return tempDir;
  }
}