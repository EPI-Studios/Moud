import axios, { AxiosResponse } from 'axios';
import fs from 'fs';
import path from 'path';
import ora from 'ora';
import * as tar from 'tar';
import AdmZip from 'adm-zip';

export class Downloader {
    public static async downloadFile(url: string, destination: string, timeout: number = 30000): Promise<void> {
        const spinner = ora(`Downloading ${path.basename(destination)}...`).start();

        try {

            await fs.promises.mkdir(path.dirname(destination), { recursive: true });

            const writer = fs.createWriteStream(destination);
            let downloadedLength = 0;

            const response: AxiosResponse = await axios({
                url,
                method: 'GET',
                responseType: 'stream',
                timeout,
                headers: {
                    'User-Agent': 'Moud-CLI/0.1.3'
                }
            });

            const totalLength = response.headers['content-length'];

            response.data.on('data', (chunk: Buffer) => {
                downloadedLength += chunk.length;
                if (totalLength) {
                    const percentage = Math.floor((downloadedLength / parseInt(totalLength, 10)) * 100);
                    spinner.text = `Downloading ${path.basename(destination)}... ${percentage}%`;
                }
            });

            response.data.on('error', (error: Error) => {
                writer.destroy();
                spinner.fail(`Download failed: ${error.message}`);
                throw error;
            });

            response.data.pipe(writer);

            return new Promise((resolve, reject) => {
                writer.on('finish', async () => {
                    try {

                        const stats = await fs.promises.stat(destination);
                        if (stats.size === 0) {
                            throw new Error('Downloaded file is empty');
                        }
                        spinner.succeed(`Downloaded ${path.basename(destination)} (${this.formatBytes(stats.size)})`);
                        resolve();
                    } catch (error) {
                        spinner.fail(`Download verification failed: ${error instanceof Error ? error.message : String(error)}`);
                        reject(error);
                    }
                });

                writer.on('error', async (err) => {
                    spinner.fail(`Failed to write ${path.basename(destination)}`);
                    try {
                        await fs.promises.unlink(destination).catch(() => {});
                    } catch {}
                    reject(err);
                });
            });

        } catch (error) {
            spinner.fail(`Failed to download from ${url}`);

            try {
                await fs.promises.unlink(destination).catch(() => {});
            } catch {}

            if (axios.isAxiosError(error)) {
                if (error.code === 'ECONNABORTED') {
                    throw new Error(`Download timeout after ${timeout}ms`);
                } else if (error.response?.status) {
                    throw new Error(`HTTP ${error.response.status}: ${error.response.statusText}`);
                }
            }

            throw error;
        }
    }

    public static async extractTarGz(source: string, destination: string): Promise<void> {
        const spinner = ora(`Extracting ${path.basename(source)}...`).start();

        try {

            await fs.promises.access(source, fs.constants.R_OK);

            await fs.promises.mkdir(destination, { recursive: true });

            await tar.x({
                file: source,
                cwd: destination,
                strip: 1,
                onwarn: (message: string) => {
                    console.warn(`Extraction warning: ${message}`);
                }
            });

            const files = await fs.promises.readdir(destination);
            if (files.length === 0) {
                throw new Error('No files were extracted');
            }

            spinner.succeed(`Extracted ${path.basename(source)}`);

        } catch (error) {
            spinner.fail(`Failed to extract ${path.basename(source)}`);

            try {
                await fs.promises.rm(destination, { recursive: true, force: true }).catch(() => {});
            } catch {}

            throw new Error(`Extraction failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    public static async extractZip(source: string, destination: string): Promise<void> {
        const spinner = ora(`Extracting ${path.basename(source)}...`).start();

        try {

            await fs.promises.access(source, fs.constants.R_OK);

            await fs.promises.mkdir(destination, { recursive: true });

            const zip = new AdmZip(source);
            zip.extractAllTo(destination, true);

            const files = await fs.promises.readdir(destination);
            if (files.length === 0) {
                throw new Error('No files were extracted');
            }

            spinner.succeed(`Extracted ${path.basename(source)}`);

        } catch (error) {
            spinner.fail(`Failed to extract ${path.basename(source)}`);

            try {
                await fs.promises.rm(destination, { recursive: true, force: true }).catch(() => {});
            } catch {}

            throw new Error(`Extraction failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private static formatBytes(bytes: number, decimals: number = 2): string {
        if (bytes === 0) return '0 Bytes';

        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];

        const i = Math.floor(Math.log(bytes) / Math.log(k));

        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    }
}