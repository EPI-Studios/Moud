import os from 'os';
import path from 'path';
import fs from 'fs';
import { exec } from 'child_process';
import { logger } from './logger.js';
import inquirer from 'inquirer';
import { Downloader } from './downloader.js';
import { VersionManager } from './version-manager.js';
import { CleanupManager } from './cleanup-manager.js';

interface PlatformInfo {
  os: string;
  arch: string;
  ext: string;
}

export class EnvironmentManager {
  private moudHome: string;
  private jdkPath: string | null = null;
  private serverJarPath: string | null = null;
  private versionManager: VersionManager;
  private cleanupManager: CleanupManager;

  constructor() {
    this.moudHome = path.join(os.homedir(), '.moud');
    this.versionManager = new VersionManager();
    this.cleanupManager = new CleanupManager();
  }

  public async initialize(): Promise<void> {
    try {
      if (!this.versionManager.validateCompatibility()) {
        throw new Error('Current CLI version is not supported. Please update to a compatible version.');
      }

      await this.ensureMoudHome();
      this.jdkPath = await this.checkAndInstallJava();
      this.serverJarPath = await this.checkAndDownloadServer();
    } catch (error) {
      logger.error('Failed to initialize environment');
      throw error;
    }
  }

  public getJavaExecutablePath(): string | null {
    return this.jdkPath;
  }

  public getServerJarPath(): string | null {
    return this.serverJarPath;
  }

  private async ensureMoudHome(): Promise<void> {
    try {
      await fs.promises.mkdir(this.moudHome, { recursive: true });
      await fs.promises.mkdir(path.join(this.moudHome, 'jdks'), { recursive: true });
      await fs.promises.mkdir(path.join(this.moudHome, 'servers'), { recursive: true });
      await fs.promises.mkdir(path.join(this.moudHome, 'temp'), { recursive: true });
    } catch (error) {
      throw new Error(`Failed to create Moud home directory: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private getPlatformInfo(): PlatformInfo {
    const platform = os.platform();
    const arch = os.arch();

    if (platform === 'darwin') {
      const mappedArch = arch === 'arm64' ? 'aarch64' : 'x64';
      return { os: 'mac', arch: mappedArch, ext: 'tar.gz' };
    }

    if (platform === 'win32') {
      return { os: 'windows', arch: 'x64', ext: 'zip' };
    }

    if (platform === 'linux') {
      const mappedArch = arch === 'arm64' ? 'aarch64' : 'x64';
      return { os: 'linux', arch: mappedArch, ext: 'tar.gz' };
    }

    throw new Error(`Unsupported platform: ${platform} ${arch}`);
  }

  private async checkAndInstallJava(): Promise<string> {
    const jdkVersion = this.versionManager.getJDKVersion();
    logger.step(`Checking for Java ${jdkVersion} installation...`);

    const localJdkDir = path.join(this.moudHome, 'jdks', jdkVersion);
    const javaExecutable = path.join(localJdkDir, 'bin', os.platform() === 'win32' ? 'java.exe' : 'java');

    if (fs.existsSync(javaExecutable)) {
      logger.success(`Found local Java ${jdkVersion} installation.`);
      return javaExecutable;
    }

    const systemJava = await this.findSystemJava();
    if (systemJava) {
      logger.success(`Found compatible system Java at: ${systemJava}`);
      return systemJava;
    }

    logger.warn(`Java ${jdkVersion} not found.`);
    const { shouldDownload } = await inquirer.prompt([{
      type: 'confirm',
      name: 'shouldDownload',
      message: `Moud requires Java ${jdkVersion}. Download a local version for Moud projects?`,
      default: true,
    }]);

    if (!shouldDownload) {
      throw new Error('Java installation cancelled. Cannot proceed.');
    }

    try {
      const platformInfo = this.getPlatformInfo();
      const jdkUrl = `https://api.adoptium.net/v3/binary/latest/21/ga/${platformInfo.os}/${platformInfo.arch}/jdk/hotspot/normal/eclipse`;
      const tempFilePath = path.join(this.moudHome, 'temp', `jdk-${jdkVersion}.${platformInfo.ext}`);

      this.cleanupManager.registerTempDir(path.dirname(tempFilePath));

      await Downloader.downloadFile(jdkUrl, tempFilePath);

      if (platformInfo.ext === 'zip') {
        await Downloader.extractZip(tempFilePath, localJdkDir);
      } else {
        await Downloader.extractTarGz(tempFilePath, localJdkDir);
      }

      await fs.promises.unlink(tempFilePath).catch(() => {});

      const extractedContents = await fs.promises.readdir(localJdkDir);
      if (extractedContents.length === 1 && (await fs.promises.stat(path.join(localJdkDir, extractedContents[0]))).isDirectory()) {
        const nestedJdkPath = path.join(localJdkDir, extractedContents[0]);
        const finalJavaExecutable = path.join(nestedJdkPath, 'bin', os.platform() === 'win32' ? 'java.exe' : 'java');

        if (fs.existsSync(finalJavaExecutable)) {
          const tempDir = path.join(this.moudHome, 'temp', 'jdk-move');
          await fs.promises.mkdir(tempDir, { recursive: true });

          const nestedContents = await fs.promises.readdir(nestedJdkPath);
          for (const item of nestedContents) {
            await fs.promises.rename(
              path.join(nestedJdkPath, item),
              path.join(tempDir, item)
            );
          }

          await fs.promises.rm(localJdkDir, { recursive: true, force: true });
          await fs.promises.rename(tempDir, localJdkDir);
        }
      }

      if (!fs.existsSync(javaExecutable)) {
        throw new Error('Java executable not found after installation');
      }

      logger.success(`Java ${jdkVersion} installed successfully for Moud.`);
      return javaExecutable;
    } catch (error) {
      throw new Error(`Failed to install Java: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private async findSystemJava(): Promise<string | null> {
    const jdkVersion = this.versionManager.getJDKVersion();

    return new Promise(resolve => {
      exec('java -version', { timeout: 5000 }, (error, stdout, stderr) => {
        if (error) {
          resolve(null);
          return;
        }

        const output = stderr || stdout;
        const versionMatch = output.match(/version "(\d+)\.?/);

        if (versionMatch && versionMatch[1] === jdkVersion) {
          resolve('java');
        } else {
          resolve(null);
        }
      });
    });
  }

  private async checkAndDownloadServer(): Promise<string> {
    const serverVersion = this.versionManager.getCompatibleEngineVersion();
    logger.step('Checking for Moud Server binary...');

    const serverFileName = `moud-server-${serverVersion}.jar`;
    const serverPath = path.join(this.moudHome, 'servers', serverFileName);

    if (fs.existsSync(serverPath)) {
      logger.success(`Found Moud Server v${serverVersion} in cache.`);
      return serverPath;
    }

    logger.info(`Moud Server v${serverVersion} not found in cache.`);

    try {
      const serverUrl = this.versionManager.getEngineDownloadUrl();
      await Downloader.downloadFile(serverUrl, serverPath);

      if (!fs.existsSync(serverPath)) {
        throw new Error('Server JAR not found after download');
      }

      return serverPath;
    } catch (error) {
      throw new Error(`Failed to download server: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
}