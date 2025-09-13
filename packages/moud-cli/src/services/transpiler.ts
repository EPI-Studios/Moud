import esbuild from 'esbuild';
import path from 'path';
import fs from 'fs';
import AdmZip from 'adm-zip';

export class Transpiler {
    private async findFiles(dir: string, extension: string): Promise<string[]> {
        const entries = await fs.promises.readdir(dir, { withFileTypes: true });
        const files = await Promise.all(entries.map(entry => {
            const res = path.resolve(dir, entry.name);
            if (entry.isDirectory()) {
                return this.findFiles(res, extension);
            }
            return res.endsWith(extension) ? [res] : [];
        }));
        return files.flat();
    }

    public async transpileProject(isProduction = false): Promise<{ server: string, client: Buffer }> {
        const projectRoot = process.cwd();
        const serverEntryPoint = path.join(projectRoot, 'src', 'main.ts');

        const serverResult = await esbuild.build({
            entryPoints: [serverEntryPoint],
            bundle: true,
            platform: 'node',
            format: 'cjs',
            target: 'es2022',
            write: false,
            minify: isProduction,
        });

        const clientDir = path.join(projectRoot, 'client');
        const zip = new AdmZip();

        if (fs.existsSync(clientDir)) {
            const clientFiles = await this.findFiles(clientDir, '.ts');
            for (const file of clientFiles) {
                const result = await esbuild.build({
                    entryPoints: [file],
                    bundle: true,
                    platform: 'neutral',
                    format: 'cjs',
                    target: 'es2022',
                    write: false,
                    minify: isProduction,
                });
                const relativePath = path.relative(clientDir, file).replace(/\.ts$/, '.js');
                zip.addFile(`scripts/${relativePath}`, Buffer.from(result.outputFiles[0].text));
            }
        }

        return {
            server: serverResult.outputFiles[0].text,
            client: zip.toBuffer(),
        };
    }
}