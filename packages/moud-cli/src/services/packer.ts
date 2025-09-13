import fs from 'fs';
import path from 'path';
import AdmZip from 'adm-zip';
import { Transpiler } from './transpiler.js';
import { EnvironmentManager } from './environment.js';
import { logger } from './logger.js';

const runShTemplate = `#!/bin/sh
java -jar server/moud-server.jar --project-root ./game
`;

const runBatTemplate = `@echo off
java -jar server\\moud-server.jar --project-root .\\game
`;

export class Packer {
    private transpiler = new Transpiler();
    private environment = new EnvironmentManager();

    public async packProject(): Promise<void> {
        const projectRoot = process.cwd();
        const packageJsonPath = path.join(projectRoot, 'package.json');
        const packageJson = JSON.parse(await fs.promises.readFile(packageJsonPath, 'utf-8'));
        const gameName = packageJson.name || 'moud-game';
        const gameVersion = packageJson.version || '1.0.0';

        const buildDir = path.join(projectRoot, '.moud-build');
        if (fs.existsSync(buildDir)) {
            await fs.promises.rm(buildDir, { recursive: true, force: true });
        }
        await fs.promises.mkdir(buildDir, { recursive: true });

        logger.step('Bundling scripts and assets...');
        const gameDir = path.join(buildDir, 'game');
        await fs.promises.mkdir(gameDir);

        const transpilationResult = await this.transpiler.transpileProject(true);
        await fs.promises.writeFile(path.join(gameDir, 'server.js'), transpilationResult.server);
        await fs.promises.writeFile(path.join(gameDir, 'client.pack'), transpilationResult.client);

        const assetsDir = path.join(projectRoot, 'assets');
        if (fs.existsSync(assetsDir)) {
            await fs.promises.cp(assetsDir, path.join(gameDir, 'assets'), { recursive: true });
        }
        logger.success('Scripts and assets bundled.');

        logger.step('Fetching Moud Server binary...');
        await this.environment.initialize();
        const serverJarPath = this.environment.getServerJarPath();
        if (!serverJarPath) {
            throw new Error('Could not retrieve Moud Server JAR.');
        }
        const serverDir = path.join(buildDir, 'server');
        await fs.promises.mkdir(serverDir);
        await fs.promises.copyFile(serverJarPath, path.join(serverDir, 'moud-server.jar'));
        logger.success('Moud Server binary fetched.');

        logger.step('Creating launcher scripts...');
        await fs.promises.writeFile(path.join(buildDir, 'run.sh'), runShTemplate, { mode: 0o755 });
        await fs.promises.writeFile(path.join(buildDir, 'run.bat'), runBatTemplate);
        logger.success('Launcher scripts created.');

        logger.step('Compressing package into a distributable zip...');
        const distDir = path.join(projectRoot, 'dist');
        await fs.promises.mkdir(distDir, { recursive: true });
        const zipFileName = `${gameName}-v${gameVersion}.zip`;
        const zipFilePath = path.join(distDir, zipFileName);

        const zip = new AdmZip();
        zip.addLocalFolder(buildDir);
        zip.writeZip(zipFilePath);
        logger.success(`Game packaged successfully!`);
        logger.info(`Package available at: dist/${zipFileName}`);

        await fs.promises.rm(buildDir, { recursive: true, force: true });
    }
}