
# Installation

This guide covers setting up Moud for development and testing

## Requirements

### Development Environment

-   **Java 21+** (for server)
-   **Node.js 18+** (for TypeScript compilation)
-   **npm or pnpm** (package manager)

### Minecraft Client

-   **Minecraft 1.21.1**
-   **Fabric Loader 0.16.14+**
-   **Moud mod**

## Step 1: Install Moud CLI

```bash
npm install -g @moud/cli
```

Verify installation:

```bash
moud --version
```

## Step 2: Install Client Mod

1.  Download and install [Fabric Loader](https://fabricmc.net/use/installer/)
2.  Download Veil mod for 1.21.1
3.  Download Moud client mod
4.  Place both mods in your `mods/` folder

## Step 3: Create Your First Project

```bash
moud create my-game
cd my-game
npm install
```

## Step 4: Start Development

```bash
npm run dev

```

This starts the Moud server on `localhost:25565`.

## Step 5: Connect and Test

1.  Launch Minecraft 1.21.1 with Fabric
2.  Connect to `localhost:25565`
3.  You should see the default TypeScript game running

## Project Structure

```
my-game/
├── src/           # Server-side TypeScript
│   └── main.ts
├── client/        # Client-side TypeScript  
│   └── main.ts
├── assets/        # Game resources
├── package.json   # Project configuration
└── tsconfig.json  # TypeScript settings

```
