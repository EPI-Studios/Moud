import crypto from 'crypto';
import esbuild from 'esbuild';
import fs from 'fs';
import path from 'path';
import AdmZip from 'adm-zip';

const IGNORED_DIRECTORIES = new Set(['node_modules', 'dist', '.moud-build', '.git', '.cache', '.gradle']);
const SUPPORTED_SOURCE_EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'];

export interface TranspileArtifacts {
    server: string;
    client: Buffer;
    hash: string;
    serverBundlePath: string;
    clientBundlePath: string;
    manifestPath: string;
    entryPoint: string;
}

export class Transpiler {
    private async findFiles(dir: string, extensions: string[]): Promise<string[]> {
        try {
            const entries = await fs.promises.readdir(dir, { withFileTypes: true });
            const files: string[] = [];

            for (const entry of entries) {
                const absolute = path.join(dir, entry.name);

                if (entry.isDirectory()) {
                    if (IGNORED_DIRECTORIES.has(entry.name)) {
                        continue;
                    }
                    files.push(...await this.findFiles(absolute, extensions));
                    continue;
                }

                const lower = entry.name.toLowerCase();
                if (extensions.some(ext => lower.endsWith(ext))) {
                    files.push(absolute);
                }
            }

            return files;
        } catch (error) {
            throw new Error(`Failed to scan directory ${dir}: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private resolveEntryPoint(projectRoot: string): string {
        const manifestPath = path.join(projectRoot, 'package.json');
        if (!fs.existsSync(manifestPath)) {
            throw new Error(`Missing package.json in ${projectRoot}`);
        }

        const manifestContent = fs.readFileSync(manifestPath, 'utf-8');
        const manifest = JSON.parse(manifestContent) as Record<string, unknown>;
        const rawEntry = (manifest['moud:main'] ?? manifest['main']) as string | undefined;

        if (!rawEntry) {
            throw new Error(`Unable to determine project entry point. Define "moud:main" in ${manifestPath}.`);
        }

        const entryPoint = path.resolve(projectRoot, rawEntry);
        if (!SUPPORTED_SOURCE_EXTENSIONS.some(ext => entryPoint.toLowerCase().endsWith(ext))) {
            throw new Error(`Unsupported entry extension for ${entryPoint}. Supported: ${SUPPORTED_SOURCE_EXTENSIONS.join(', ')}`);
        }

        if (!fs.existsSync(entryPoint)) {
            throw new Error(`Declared entry point "${rawEntry}" does not exist at ${entryPoint}`);
        }

        return entryPoint;
    }

    private async persistArtifacts(projectRoot: string, server: string, client: Buffer, hash: string, entryPoint: string) {
        const cacheDir = path.join(projectRoot, '.moud', 'cache');
        await fs.promises.mkdir(cacheDir, { recursive: true });

        const serverBundlePath = path.join(cacheDir, 'server.bundle.js');
        const clientBundlePath = path.join(cacheDir, 'client.bundle');
        const manifestPath = path.join(cacheDir, 'manifest.json');

        await Promise.all([
            fs.promises.writeFile(serverBundlePath, server, 'utf-8'),
            fs.promises.writeFile(clientBundlePath, client),
            fs.promises.writeFile(
                manifestPath,
                JSON.stringify(
                    {
                        hash,
                        entryPoint: path.relative(projectRoot, entryPoint).replace(/\\/g, '/'),
                        generatedAt: new Date().toISOString()
                    },
                    null,
                    2
                ),
                'utf-8'
            )
        ]);

        return { serverBundlePath, clientBundlePath, manifestPath };
    }

    public async transpileProject(isProduction = false): Promise<TranspileArtifacts> {
        const projectRoot = process.cwd();
        const entryPoint = this.resolveEntryPoint(projectRoot);

        try {
            const serverResult = await esbuild.build({
                absWorkingDir: projectRoot,
                entryPoints: [entryPoint],
                bundle: true,
                platform: 'node',
                format: 'esm',
                target: 'es2022',
                write: false,
                minify: isProduction,
                sourcemap: !isProduction,
                external: ['@epi-studio/moud-sdk']
            });

            const serverOutput = serverResult.outputFiles[0]?.text;
            if (!serverOutput) {
                throw new Error('esbuild did not emit server output');
            }

            const clientDir = path.join(projectRoot, 'client');
            const zip = new AdmZip();

            if (fs.existsSync(clientDir)) {
                const clientFiles = await this.findFiles(clientDir, SUPPORTED_SOURCE_EXTENSIONS);

                for (const file of clientFiles) {
                    const result = await esbuild.build({
                        absWorkingDir: clientDir,
                        entryPoints: [file],
                        bundle: true,
                        platform: 'browser',
                        format: 'iife',
                        target: 'es2022',
                        write: false,
                        minify: isProduction,
                        sourcemap: !isProduction
                    }).catch(error => {
                        throw new Error(`Failed to transpile client file ${path.relative(projectRoot, file)}: ${error instanceof Error ? error.message : String(error)}`);
                    });

                    const jsOutput = result.outputFiles[0]?.text;
                    if (!jsOutput) {
                        throw new Error(`esbuild did not emit output for client file ${path.relative(projectRoot, file)}`);
                    }

                    const relativePath = path.relative(clientDir, file).replace(/\.[^.]+$/, '.js').replace(/\\/g, '/');
                    zip.addFile(`scripts/${relativePath}`, Buffer.from(jsOutput));

                    const sourcemap = result.outputFiles.find(out => out.path.endsWith('.map'));
                    if (sourcemap) {
                        zip.addFile(`scripts/${relativePath}.map`, Buffer.from(sourcemap.text));
                    }
                }
            }

            const clientBuffer = zip.toBuffer();
            const hash = crypto.createHash('sha256').update(serverOutput).update(clientBuffer).digest('hex');

            const persistence = await this.persistArtifacts(projectRoot, serverOutput, clientBuffer, hash, entryPoint);

            return {
                server: serverOutput,
                client: clientBuffer,
                hash,
                serverBundlePath: persistence.serverBundlePath,
                clientBundlePath: persistence.clientBundlePath,
                manifestPath: persistence.manifestPath,
                entryPoint
            };
        } catch (error) {
            throw new Error(`Transpilation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}
