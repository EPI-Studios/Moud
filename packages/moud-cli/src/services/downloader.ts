import axios from 'axios';
import fs from 'fs';
import path from 'path';
import ora from 'ora';
import * as tar from 'tar';

export class Downloader {
    public static async downloadFile(url: string, destination: string): Promise<void> {
        const spinner = ora(`Downloading ${path.basename(destination)}...`).start();
        const writer = fs.createWriteStream(destination);

        try {
            const response = await axios({
                url,
                method: 'GET',
                responseType: 'stream',
            });

            const totalLength = response.headers['content-length'];
            let downloadedLength = 0;

            response.data.on('data', (chunk: Buffer) => {
                downloadedLength += chunk.length;
                if (totalLength) {
                    const percentage = Math.floor((downloadedLength / parseInt(totalLength, 10)) * 100);
                    spinner.text = `Downloading ${path.basename(destination)}... ${percentage}%`;
                }
            });

            response.data.pipe(writer);

            return new Promise((resolve, reject) => {
                writer.on('finish', () => {
                    spinner.succeed(`Downloaded ${path.basename(destination)}`);
                    resolve();
                });
                writer.on('error', (err) => {
                    spinner.fail(`Failed to download ${path.basename(destination)}`);
                    reject(err);
                });
            });
        } catch (error) {
            spinner.fail(`Failed to download from ${url}`);
            throw error;
        }
    }

    public static async extractTarGz(source: string, destination: string): Promise<void> {
        const spinner = ora(`Extracting ${path.basename(source)}...`).start();
        try {
            await fs.promises.mkdir(destination, { recursive: true });
            await tar.x({
                file: source,
                cwd: destination,
                strip: 1
            });
            spinner.succeed(`Extracted ${path.basename(source)}`);
        } catch (error) {
            spinner.fail(`Failed to extract ${path.basename(source)}`);
            throw error;
        }
    }
}