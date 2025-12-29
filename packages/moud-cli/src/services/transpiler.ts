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
    shared: string | null;
    hash: string;
    serverBundlePath: string;
    clientBundlePath: string;
    sharedBundlePath: string | null;
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

    private async persistArtifacts(projectRoot: string, server: string, client: Buffer, shared: string | null, hash: string, entryPoint: string) {
        const cacheDir = path.join(projectRoot, '.moud', 'cache');
        await fs.promises.mkdir(cacheDir, { recursive: true });

        const serverBundlePath = path.join(cacheDir, 'server.bundle.js');
        const clientBundlePath = path.join(cacheDir, 'client.bundle');
        const sharedBundlePath = shared ? path.join(cacheDir, 'shared.bundle.js') : null;
        const manifestPath = path.join(cacheDir, 'manifest.json');

        const writes: Promise<void>[] = [
            fs.promises.writeFile(serverBundlePath, server, 'utf-8'),
            fs.promises.writeFile(clientBundlePath, client),
            fs.promises.writeFile(
                manifestPath,
                JSON.stringify(
                    {
                        hash,
                        entryPoint: path.relative(projectRoot, entryPoint).replace(/\\/g, '/'),
                        hasSharedPhysics: shared !== null,
                        generatedAt: new Date().toISOString()
                    },
                    null,
                    2
                ),
                'utf-8'
            )
        ];

        if (shared && sharedBundlePath) {
            writes.push(fs.promises.writeFile(sharedBundlePath, shared, 'utf-8'));
        }

        await Promise.all(writes);

        return { serverBundlePath, clientBundlePath, sharedBundlePath, manifestPath };
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
            const sharedDir = path.join(projectRoot, 'shared');
            const zip = new AdmZip();

            // Transpile shared physics scripts (runs on both client and server)
            let sharedOutput: string | null = null;
            const sharedPhysicsEntry = path.join(sharedDir, 'physics', 'index.ts');
            if (fs.existsSync(sharedPhysicsEntry)) {
                const sharedResult = await esbuild.build({
                    absWorkingDir: projectRoot,
                    entryPoints: [sharedPhysicsEntry],
                    bundle: true,
                    platform: 'neutral',
                    format: 'cjs',
                    target: 'es2022',
                    write: false,
                    minify: isProduction,
                    sourcemap: !isProduction,
                    external: ['@epi-studio/moud-sdk']
                }).catch(error => {
                    throw new Error(`Failed to transpile shared physics: ${error instanceof Error ? error.message : String(error)}`);
                });

                sharedOutput = sharedResult.outputFiles[0]?.text ?? null;
                if (sharedOutput) {
                    zip.addFile('scripts/shared/physics.js', Buffer.from(sharedOutput));
                }
            }

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
            const hashInput = crypto.createHash('sha256').update(serverOutput).update(clientBuffer);
            if (sharedOutput) {
                hashInput.update(sharedOutput);
            }
            const hash = hashInput.digest('hex');

            const persistence = await this.persistArtifacts(projectRoot, serverOutput, clientBuffer, sharedOutput, hash, entryPoint);

            return {
                server: serverOutput,
                client: clientBuffer,
                shared: sharedOutput,
                hash,
                serverBundlePath: persistence.serverBundlePath,
                clientBundlePath: persistence.clientBundlePath,
                sharedBundlePath: persistence.sharedBundlePath,
                manifestPath: persistence.manifestPath,
                entryPoint
            };
        } catch (error) {
            throw new Error(`Transpilation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}
