import fs from 'fs';
import path from 'path';
import { logger } from './logger.js';
import { execSync } from 'child_process';

const packageJsonTemplate = (projectName: string) => `{
  "name": "${projectName}",
  "version": "1.0.0",
  "description": "A new game powered by Moud",
  "moud:main": "src/main.ts",
  "scripts": {
    "dev": "moud dev",
    "build": "moud build",
    "pack-game": "moud pack"
  },
  "devDependencies": {
    "@moud/sdk": "0.1.0-alpha"
  }
}`;

const tsconfigTemplate = `{
  "compilerOptions": {
    "target": "ES2022",
    "module": "CommonJS",
    "moduleResolution": "node",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "noEmit": true,
    "types": ["@moud/sdk"]
  },
  "include": [
    "src/**/*",
    "client/**/*"
  ]
}`;

const gitignoreTemplate = `node_modules
dist
.moud-build
.DS_Store
`;

const serverMainTemplate = `import type { Player } from '@moud/sdk/server';

console.log("Welcome to your new Moud game!");

api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 65, 0);

api.on('player.join', (player: Player) => {
    player.sendMessage(\`Welcome to the server, \${player.getName()}!\`);
});
`;

const clientMainTemplate = `console.log("Moud client script loaded.");

Moud.input.onKey("key.keyboard.f", (isPressed: boolean) => {
    if (isPressed) {
        console.log("Key 'F' was pressed!");
    }
});
`;

export async function createProjectStructure(projectName: string): Promise<void> {
    const projectDir = path.join(process.cwd(), projectName);
    logger.step(`Creating project directory at: ${projectDir}`);

    if (fs.existsSync(projectDir)) {
        throw new Error(`Directory '${projectName}' already exists.`);
    }
    await fs.promises.mkdir(projectDir, { recursive: true });

    const srcDir = path.join(projectDir, 'src');
    const clientDir = path.join(projectDir, 'client');
    const assetsDir = path.join(projectDir, 'assets');

    await fs.promises.mkdir(srcDir);
    await fs.promises.mkdir(clientDir);
    await fs.promises.mkdir(assetsDir);

    await fs.promises.writeFile(path.join(projectDir, 'package.json'), packageJsonTemplate(projectName));
    await fs.promises.writeFile(path.join(projectDir, 'tsconfig.json'), tsconfigTemplate);
    await fs.promises.writeFile(path.join(projectDir, '.gitignore'), gitignoreTemplate);
    await fs.promises.writeFile(path.join(srcDir, 'main.ts'), serverMainTemplate);
    await fs.promises.writeFile(path.join(clientDir, 'main.ts'), clientMainTemplate);

    logger.step('Installing dependencies...');
    execSync('pnpm install', { cwd: projectDir, stdio: 'inherit' });

    logger.success(`Project '${projectName}' created successfully!`);
    logger.info(`Navigate to your project:`);
    logger.info(`  cd ${projectName}`);
    logger.info(`Run the development server:`);
    logger.info(`  pnpm dev`);
}