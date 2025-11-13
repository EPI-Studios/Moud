
# Moud
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white) 
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) 
![Powered by Minestom](https://img.shields.io/badge/Powered%20by-Minestom-815db1?style=for-the-badge&logo=java&logoColor=white) 
![Running on Fabric](https://img.shields.io/badge/Running%20on-Fabric-2C2C2C?style=for-the-badge&logo=java&logoColor=white) 
[![Wiki](https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=book&logoColor=white)](https://moud.epistudios.fr/)


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

## Credits & Acknowledgments
-   [Minestom](https://github.com/Minestom/Minestom) For providing the incredibly powerful and modern Minecraft server base
-   [GraalVM](https://github.com/oracle/graal) For enabling high-performance JavaScript & TypeScript execution on the JVM
-   [FabricMC](https://github.com/FabricMC/fabric) For theclient-side modding toolchain and API that makes the Moud client possible
-   [Veil](https://github.com/foundry-mc/veil) For the advanced rendering capabilities, making custom shaders and post-processing easier to handle
-   [PlayerAnimationLib](https://github.com/ZigyTheBird/PlayerAnimationLibrary/tree/1.21.1) [@ZigyTheBird](https://github.com/ZigyTheBird) For the powerful and flexible client-side animation system
-   [mdoc](https://github.com/Meekiavelique/mdoc) For providing the foundation for the documentation website


----------


**Created by [@Meekiavelique](https://github.com/Meekiavelique)**  
**EPI STUDIO** - [Discord](https://discord.gg/PvKeHzTwdU)

[![EPI STUDIO Banner](https://i.postimg.cc/50yBMFKZ/Banniereepistudio.jpg)](https://postimg.cc/Fdtdy14Z)
