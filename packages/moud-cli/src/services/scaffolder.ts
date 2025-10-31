import fs from 'fs';
import path from 'path';
import { logger } from './logger.js';
import { VersionManager } from './version-manager.js';

const versionManager = new VersionManager();

const packageJsonTemplate = (projectName: string) => {
  const cliVersion = versionManager.getCurrentCliVersion();
  const sdkVersion = versionManager.getCompatibleSDKVersion();

  return {
    name: projectName,
    version: "0.1.0",
    description: `A Moud game: ${projectName}`,
    "moud:main": "src/main.ts",
    scripts: {
      dev: "moud dev",
      pack: "moud pack"
    },
    packageManager: "pnpm@9",
    devDependencies: {
      "@epi-studio/moud-sdk": sdkVersion.startsWith("^") ? sdkVersion : `^${sdkVersion}`,
      "@epi-studio/moud-cli": cliVersion.startsWith("^") ? cliVersion : `^${cliVersion}`,
      "typescript": "^5.4.0"
    }
  };
};

const mainTsTemplate = `/// <reference types="@epi-studio/moud-sdk" />

console.log('Game server starting...');

api.on('player.join', (player) => {
  console.log(\`Player \${player.getName()} joined the game\`);
  player.sendMessage('Welcome to the game!');

  api.getServer().broadcast(\`\${player.getName()} has joined the game!\`);
});

api.on('player.leave', (player) => {
  console.log(\`Player \${player.getName()} left the game\`);
  api.getServer().broadcast(\`\${player.getName()} has left the game!\`);
});

api.on('player.chat', (event) => {
  const player = event.getPlayer();
  const message = event.getMessage();

  console.log(\`[\${player.getName()}]: \${message}\`);

  if (message.toLowerCase() === '/spawn') {
    event.cancel();
    player.teleport(0, 64, 0);
    player.sendMessage('Teleported to spawn!');
  }
});

const world = api.getWorld();
world.setFlatGenerator();
world.setSpawn(8.5, 66, 8.5);

console.log('Game server initialized!');
`;

const clientMainTsTemplate = `console.log('Client script loaded');

window.addEventListener('moud:custom', (event) => {
  console.log('Received custom event:', event.detail);
});
`;

const tsconfigTemplate = {
  compilerOptions: {
    target: "ES2022",
    module: "ESNext",
    moduleResolution: "node",
    esModuleInterop: true,
    allowSyntheticDefaultImports: true,
    strict: true,
    skipLibCheck: true,
    forceConsistentCasingInFileNames: true,
    lib: ["ES2022"],
    allowImportingTsExtensions: true,
    noEmit: true
  },
  include: ["src/**/*", "client/**/*"],
  exclude: ["node_modules", "dist"]
};

const readmeTemplate = (projectName: string) => `# ${projectName}

A Moud game project.

## Getting Started

1. Install dependencies:
   \`\`\`bash
   pnpm install
   \`\`\`

2. Start development server:
   \`\`\`bash
   pnpm run dev
   \`\`\`

3. Build for distribution:
   \`\`\`bash
   pnpm run pack
   \`\`\`

## Project Structure

- \`src/main.ts\` - Server-side game logic
- \`client/\` - Client-side scripts
- \`assets/\` - Game assets (textures, models, etc.)

## Documentation

Visit [Moud Documentation](https://moud.dev) for more information.
`;

export async function createProjectStructure(projectName: string): Promise<void> {
  const projectDir = path.join(process.cwd(), projectName);

  logger.step(`Creating project directory: ${projectName}`);

  if (fs.existsSync(projectDir)) {
    throw new Error(`Directory '${projectName}' already exists.`);
  }

  await fs.promises.mkdir(projectDir);
  await fs.promises.mkdir(path.join(projectDir, 'src'));
  await fs.promises.mkdir(path.join(projectDir, 'client'));
  await fs.promises.mkdir(path.join(projectDir, 'assets'));

  logger.step('Creating package.json...');
  await fs.promises.writeFile(
    path.join(projectDir, 'package.json'),
    JSON.stringify(packageJsonTemplate(projectName), null, 2)
  );

  logger.step('Creating main server file...');
  await fs.promises.writeFile(
    path.join(projectDir, 'src', 'main.ts'),
    mainTsTemplate
  );

  logger.step('Creating client script...');
  await fs.promises.writeFile(
    path.join(projectDir, 'client', 'main.ts'),
    clientMainTsTemplate
  );

  logger.step('Creating TypeScript configuration...');
  await fs.promises.writeFile(
    path.join(projectDir, 'tsconfig.json'),
    JSON.stringify(tsconfigTemplate, null, 2)
  );

  logger.step('Creating README...');
  await fs.promises.writeFile(
    path.join(projectDir, 'README.md'),
    readmeTemplate(projectName)
  );

  logger.step('Creating .gitignore...');
  await fs.promises.writeFile(
    path.join(projectDir, '.gitignore'),
    `node_modules/
dist/
.moud-build/
*.log
.DS_Store
`
  );

  logger.success(`Project '${projectName}' created successfully!`);
  logger.info(`Next steps:`);
  logger.info(`  cd ${projectName}`);
  logger.info(`  npm install`);
  logger.info(`  npm run dev`);
}
