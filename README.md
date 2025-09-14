# Moud
[![Project Status: Alpha](https://img.shields.io/badge/status-Alpha-orange.svg)](https://github.com/Epitygmata/moud)   [![Powered by Minestom](https://img.shields.io/badge/Powered%20by-Minestom-815db1?logo=java)](https://minestom.net/)   [![Runs on Fabric](https://img.shields.io/badge/Runs%20on-Fabric-d64e29?logo=fabric)](https://fabricmc.net/)   [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**Moud is a modern scripting engine for Minecraft that puts TypeScript at the center of game development.**

Instead of wrestling with Java compilation cycles, write your entire game, server logic, client interactions, UI systems, and real-time graphics in TypeScript

## 🚀 Quick Start

```bash
# Install the CLI
npm install -g @epi-studio/moud-cli@latest

# Create a new project
moud create my-game
cd my-game

# Start developing
npm run dev

```

## Features

### **Real-Time State Synchronization**

The **Shared Values** system automatically synchronizes game state between server and client with reactive updates and conflict resolution.

### **Client Rendering**

Handle **Veil's** rendering pipeline through TypeScript for custom shaders, post-processing effects, and dynamic lighting systems.

### **UI Framework**

Build responsive, interactive interfaces using a Flexbox-inspired component system with real-time asset streaming (still buggy).

Built on a solid, and very very very very lightweight library named **Minestom** for the server and **Fabric** for the client.

### **Excellent Core**
 TypeScript/JavaScript code is executed in a high-performance, sandboxed GraalVM environment on both the server and the client
 
##  Architecture

**Server (Minestom + GraalVM)**

-   Executes server-side TypeScript logic
-   Manages world state and entity systems
-   Handles networking and synchronization
-   Serves assets and scripts to clients

**Client (Fabric Mod + GraalVM)**

-   Receives and executes client-side scripts
-   Renders custom UI components
-   Manages input handling and camera systems
-   Integrates with Veil for advanced rendering


## Development Status

**Working Features**

-   Full TypeScript server/client scripting
-   Shared Values state synchronization
-   Component-based UI system
-   Asset packaging and streaming
-   Event-driven architecture
-   Dynamic lighting system
-   Custom camera controls

**In Development**

-   Hot-reloading for scripts and assets
-   Advanced Veil integration
-   Script debugging tools
-   Performance profiling
-   Production deployment tools



**Planned**

-   Handle level making (using Axiom [Issue #11](https://github.com/EPI-Studios/Moud/issues/11))
-   Asset pipeline optimization
-   Server instancing
-   Default rendering examples (bloom.. [Issue #9](https://github.com/EPI-Studios/Moud/issues/9))
-   Audio handling and engine using OpenAL ([Issue #6](https://github.com/EPI-Studios/Moud/issues/6))


## Requirements

**Server**

-   Java 21+
-   Node.js 18+ (for TypeScript compilation)

**Client**

-   Minecraft 1.21.1
-   Fabric Loader 0.16.14+
-   The mod

## Contributing

We welcome contributions! Whether it's bug fixes, feature additions, or documentation improvements.

1.  Fork the repository
2.  Create a feature branch
3.  Make your changes
4.  Add tests if applicable
5.  Submit a pull request

See CONTRIBUTING.md for guidelines.

## License

Moud is open source software licensed under the MIT License.

## Credits

**Created and maintained by [@Meekiavelique](https://github.com/Meekiavelique)**

**Powered by EPI STUDIO**

-   Discord: https://discord.gg/PvKeHzTwdU

[![EPI STUDIO Banner](https://i.postimg.cc/50yBMFKZ/Banniereepistudio.jpg)](https://postimg.cc/Fdtdy14Z)

----------

**Ready to build something?** Get started with Moud → (Wiki is comming soon)
