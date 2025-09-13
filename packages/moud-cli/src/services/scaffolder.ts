import fs from 'fs';
import path from 'path';
import { logger } from './logger.js';
import { execSync } from 'child_process';

function findMonorepoRoot(startDir: string): string | null {
    let currentDir = startDir;
    while (currentDir !== path.parse(currentDir).root) {
        if (fs.existsSync(path.join(currentDir, 'pnpm-workspace.yaml'))) {
            return currentDir;
        }
        currentDir = path.dirname(currentDir);
    }
    return null;
}

export async function createProjectStructure(projectName: string): Promise<void> {
    const monorepoRoot = findMonorepoRoot(process.cwd());

    if (!monorepoRoot) {
        throw new Error("Could not find monorepo root. Make sure you are running this command from within the moud project.");
    }

    const templateDir = path.join(monorepoRoot, 'example', 'ts');
    const projectDir = path.join(monorepoRoot, 'example', projectName);

    logger.step(`Creating project in: ${projectDir}`);

    if (!fs.existsSync(templateDir)) {
        throw new Error(`Template directory not found at: ${templateDir}`);
    }
    if (fs.existsSync(projectDir)) {
        throw new Error(`Directory '${projectName}' already exists in the example folder.`);
    }

    await fs.promises.cp(templateDir, projectDir, { recursive: true });
    logger.success(`Copied template to '${projectName}'`);

    const packageJsonPath = path.join(projectDir, 'package.json');
    if (fs.existsSync(packageJsonPath)) {
        try {
            const packageJsonContent = await fs.promises.readFile(packageJsonPath, 'utf-8');
            const packageJson = JSON.parse(packageJsonContent);

            packageJson.name = projectName;

            const newPackageJson = {
              name: projectName,
              version: "1.0.0",
              description: `A new Moud game: ${projectName}`,
              "moud:main": "src/main.ts",
              scripts: {
                dev: `pnpm --filter ${projectName} dev-server`,
                build: `pnpm --filter ${projectName} build-game`
              },
              devDependencies: {
                "@epi-studio/moud-sdk": "workspace:*",
                "@epi-studio/moud-cli": "workspace:*"
              }
            };

            await fs.promises.writeFile(packageJsonPath, JSON.stringify(newPackageJson, null, 2));
            logger.success(`Updated package.json for '${projectName}'`);
        } catch (error) {
            throw new Error(`Failed to update package.json: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    logger.step('Linking new project and installing dependencies...');
    execSync('pnpm install', { cwd: monorepoRoot, stdio: 'inherit' });

    logger.success(`Project '${projectName}' created successfully!`);
    logger.info(`You can now run your new game example:`);
    logger.info(`  pnpm --filter ${projectName} dev`);
}