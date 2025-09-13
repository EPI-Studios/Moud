import os from 'os';
import path from 'path';
import fs from 'fs';
import { exec } from 'child_process';
import { logger } from './logger.js';
import inquirer from 'inquirer';
import { Downloader } from './downloader.js';

const MOUD_JDK_VERSION = '21';
const MOUD_SERVER_VERSION = '0.1.0-alpha';

export class EnvironmentManager {
    private moudHome: string;
    private jdkPath: string | null = null;
    private serverJarPath: string | null = null;

    constructor() {
        this.moudHome = path.join(os.homedir(), '.moud');
    }

    public async initialize(): Promise<void> {
        await this.ensureMoudHome();
        this.jdkPath = await this.checkAndInstallJava();
        this.serverJarPath = await this.checkAndDownloadServer();
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
        } catch (error) {
            logger.error('Failed to create Moud home directory.');
            throw error;
        }
    }

    private getPlatformInfo(): { os: string; arch: string; ext: string } {
        const platform = os.platform();
        const arch = os.arch();

        if (platform === 'darwin') return { os: 'macos', arch: arch, ext: 'tar.gz' };
        if (platform === 'win32') return { os: 'windows', arch: 'x64', ext: 'zip' };
        return { os: 'linux', arch: 'x64', ext: 'tar.gz' };
    }

    private async checkAndInstallJava(): Promise<string> {
        logger.step(`Checking for Java ${MOUD_JDK_VERSION} installation...`);

        const localJdkDir = path.join(this.moudHome, 'jdks', MOUD_JDK_VERSION);
        const javaExecutable = path.join(localJdkDir, 'bin', os.platform() === 'win32' ? 'java.exe' : 'java');

        if (fs.existsSync(javaExecutable)) {
            logger.success(`Found local Java ${MOUD_JDK_VERSION} installation.`);
            return javaExecutable;
        }

        const systemJava = await this.findSystemJava();
        if (systemJava) {
            logger.success(`Found compatible system Java at: ${systemJava}`);
            return systemJava;
        }

        logger.warn(`Java ${MOUD_JDK_VERSION} not found.`);
        const { shouldDownload } = await inquirer.prompt([{
            type: 'confirm',
            name: 'shouldDownload',
            message: `Moud requires Java ${MOUD_JDK_VERSION}. Download a local version for Moud projects?`,
            default: true,
        }]);

        if (!shouldDownload) {
            logger.error('Java installation cancelled. Cannot proceed.');
            process.exit(1);
        }

        const { os: platformOs, arch, ext } = this.getPlatformInfo();
        const jdkUrl = `https://api.adoptium.net/v3/binary/latest/21/ga/${platformOs}/${arch}/jdk/hotspot/normal/eclipse`;
        const tempFilePath = path.join(this.moudHome, `jdk-${MOUD_JDK_VERSION}.${ext}`);

        await Downloader.downloadFile(jdkUrl, tempFilePath);
        await Downloader.extractTarGz(tempFilePath, localJdkDir);
        await fs.promises.unlink(tempFilePath);

        logger.success(`Java ${MOUD_JDK_VERSION} installed successfully for Moud.`);
        return javaExecutable;
    }

    private async findSystemJava(): Promise<string | null> {
        return new Promise(resolve => {
            exec('java -version', (error, stdout, stderr) => {
                const output = stderr || stdout;
                if (output.includes(`version "${MOUD_JDK_VERSION}.`)) {
                    resolve('java');
                } else {
                    resolve(null);
                }
            });
        });
    }

    private async checkAndDownloadServer(): Promise<string> {
        logger.step('Checking for Moud Server binary...');
        const serverFileName = `moud-server-${MOUD_SERVER_VERSION}.jar`;
        const serverPath = path.join(this.moudHome, 'servers', serverFileName);

        if (fs.existsSync(serverPath)) {
            logger.success(`Found Moud Server v${MOUD_SERVER_VERSION} in cache.`);
            return serverPath;
        }

        logger.info(`Moud Server v${MOUD_SERVER_VERSION} not found in cache.`);
        const serverUrl = `https://github.com/Epitygmata/moud/releases/download/v${MOUD_SERVER_VERSION}/moud-server.jar`;

        await Downloader.downloadFile(serverUrl, serverPath);
        return serverPath;
    }
}