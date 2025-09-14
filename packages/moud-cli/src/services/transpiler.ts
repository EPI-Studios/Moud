import esbuild from 'esbuild';
import path from 'path';
import fs from 'fs';
import AdmZip from 'adm-zip';

export class Transpiler {
    private async findFiles(dir: string, extension: string): Promise<string[]> {
        try {
            const entries = await fs.promises.readdir(dir, { withFileTypes: true });
            const files = await Promise.all(entries.map(async entry => {
                const res = path.resolve(dir, entry.name);
                if (entry.isDirectory()) {
                    return this.findFiles(res, extension);
                }
                return res.endsWith(extension) ? [res] : [];
            }));
            return files.flat();
        } catch (error) {
            throw new Error(`Failed to find files in ${dir}: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    public async transpileProject(isProduction = false): Promise<{ server: string, client: Buffer }> {
        const projectRoot = process.cwd();
        const serverEntryPoint = path.join(projectRoot, 'src', 'main.ts');

        if (!fs.existsSync(serverEntryPoint)) {
            throw new Error(`Server entry point not found: ${serverEntryPoint}`);
        }

        try {
            const serverResult = await esbuild.build({
                entryPoints: [serverEntryPoint],
                bundle: true,
                platform: 'node',
                format: 'esm',
                target: 'es2022',
                write: false,
                minify: isProduction,
                sourcemap: !isProduction,
                external: ['@epi-studio/moud-sdk']
            });

            const clientDir = path.join(projectRoot, 'client');
            const zip = new AdmZip();

            if (fs.existsSync(clientDir)) {
                const clientFiles = await this.findFiles(clientDir, '.ts');

                for (const file of clientFiles) {
                    try {
                        const result = await esbuild.build({
                            entryPoints: [file],
                            bundle: true,
                            platform: 'browser',
                            format: 'esm',
                            target: 'es2022',
                            write: false,
                            minify: isProduction,
                            sourcemap: !isProduction
                        });

                        const relativePath = path.relative(clientDir, file).replace(/\.ts$/, '.js');
                        zip.addFile(`scripts/${relativePath}`, Buffer.from(result.outputFiles[0].text));

                        if (result.outputFiles[1]) {
                            zip.addFile(`scripts/${relativePath}.map`, Buffer.from(result.outputFiles[1].text));
                        }
                    } catch (error) {
                        throw new Error(`Failed to transpile client file ${file}: ${error instanceof Error ? error.message : String(error)}`);
                    }
                }
            }

            return {
                server: serverResult.outputFiles[0].text,
                client: zip.toBuffer(),
            };
        } catch (error) {
            throw new Error(`Transpilation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}