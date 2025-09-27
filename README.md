
# Moud

![TypeScript](https://img.shields.io/badge/typescript-%23007ACC.svg?style=for-the-badge&logo=typescript&logoColor=white) ![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white) 

![Powered by Minestom](https://img.shields.io/badge/Powered%20by-Minestom-815db1?logo=java) ![Running on Fabric](https://img.shields.io/badge/Running%20on-Fabric-815db1?logo=java)

**TypeScript-first Minecraft scripting framework**

Write server logic, client modifications, and graphics code in TypeScript.

```bash
npm install -g @epi-studio/moud-cli@latest
moud create 
cd game-name
moud dev
```

## Core Features

**State Synchronization** - Shared Values automatically sync game state between server and client with reactive updates and conflict resolution

**Client Rendering** - Direct access to Veil's rendering pipeline for custom shaders, post-processing, and dynamic lighting

**UI System** - Flexbox-inspired component system (not sure it  works great)

**Performance** - GraalVM execution environment on both server and client for fast TypeScript/JavaScript

## Architecture

**Server (Minestom + GraalVM)**

-   TypeScript server logic execution
-   World state and entity management
-   Networking and synchronization
-   Asset serving

**Client (Fabric Mod + GraalVM)**

-   Client-side script execution
-   Custom UI rendering
-   Input and camera handling
-   Veil rendering integration

## Status

**Working**

-   Full TypeScript server/client scripting
-   Shared Values state sync
-   Component UI system
-   Asset packaging and streaming
-   Event system
-   Dynamic lighting
-   Custom camera controls

**In Development**
-   Advanced Veil integration
-   Script debugging
-   Performance profiling
-   Production deployment

**Planned**

-   Level editor integration (Axiom [#11](https://github.com/EPI-Studios/Moud/issues/11))
-   Asset pipeline optimization
-   Server instancing
-   Rendering examples (bloom [#9](https://github.com/EPI-Studios/Moud/issues/9))
-   Audio engine with OpenAL ([#6](https://github.com/EPI-Studios/Moud/issues/6))

## Requirements


**Server**: Java 21+
**Client**: A Computer with Minecraft 1.21.1 (fabric) and the mod


## Contributing

Fork, branch, code, test, PR. See CONTRIBUTING.md for details.

## License

MIT License

----------

**Created by [@Meekiavelique](https://github.com/Meekiavelique)**  
**EPI STUDIO** - [Discord](https://discord.gg/PvKeHzTwdU)

[![EPI STUDIO Banner](https://i.postimg.cc/50yBMFKZ/Banniereepistudio.jpg)](https://postimg.cc/Fdtdy14Z)
