

# Moud

[](https://www.google.com/url?sa=E&q=https%3A%2F%2Fopensource.org%2Flicenses%2FMIT)

[![CI Status](https://github.com/Epitygmata/moud/actions/workflows/ci.yml/badge.svg)](https://github.com/Epitygmata/moud/actions/workflows/ci.yml)  [![Project Status: WIP](https://img.shields.io/badge/status-WIP-orange.svg)](https://github.com/Epitygmata/moud) [![Powered by Minestom](https://img.shields.io/badge/Powered%20by-Minestom-815db1?logo=java)](https://minestom.net/)  
[![Runs on Fabric](https://img.shields.io/badge/Runs%20on-Fabric-d64e29?logo=fabric)](https://fabricmc.net/)

**Moud is (kinda like) game engine for Minecraft, designed with a "script-first" approach based on typescript**

Forget long Java compilation cycles; write your server-side and client-side game logic in TypeScript and see your changes come to life instantly

## Features

- **Scripting First:** Write both server logic (gameplay, events) and client logic (UI, rendering, input) entirely in **TypeScript** or JavaScript (others languages if someone have courage to pull-request it).

- **Powered by GraalVM:** TypeScript/JavaScript code is executed in a high-performance, sandboxed* GraalVM environment on both the server and the client.

- **State Synchronization:** The **"Shared Values"** system allows state (e.g., player stats, inventory) to be seamlessly synchronized between the server and client with a simple, reactive API.

- **Modern UI Engine:** Create complex, performant user interfaces using a Flexbox-inspired component system, directly from your TypeScript scripts. Assets (images, fonts) are dynamically pushed from the server to clients.

- **Excellent Core:** Built on a solid, and very very very very lightweight library named **Minestom** for the server and **Fabric** for the client.

- **Integrated Tooling:** The project includes moud-cli, a command-line interface to simplify development, with features like hot-reloading planned (not yet done).


## Architecture

1. **The Server (server):** A Minestom-based Java application that embeds the GraalVM engine. It handles game logic, entities, worlds, and acts as the single source of truth. This is where your server-side script runs.

2. **The Client (client-mod):** A Fabric mod for Minecraft that injects a GraalVM engine on the client side. It receives scripts and assets from the server, handles custom UI rendering, and manages user input and Veil.

## Project Status

**Current Stage:** Alpha

**What's Working:**
- Server-side scripting with TypeScript/JavaScript
- Client-side script synchronization and execution
- Basic UI system with component rendering
- Shared Values state synchronization
- Asset packaging and distribution
- Event system for player interactions

**What's Planned:**
- More advanced Veil integration
- Dynamic asset loading for large assets
- Client customization
- Script debugging tools
- Production-ready sandboxing

## Known Limitations

**Runtime & Performance**
- GraalVM context initialization overhead on client connections
- JavaScript execution timeout limits (30 seconds currently)
- Memory constraints per script context (256MB heap size)
- Single-threaded script execution model

**API Stability**
- Scripting APIs are not yet stabilized and may change between versions
- Shared Values synchronization protocol may evolve
- Client-server networking packet format not finalized

**Platform Dependencies**
- Requires specific Minecraft version (because of veil) (1.21.1)
- Veil integration still experimental

**Feature Completeness**
- Permission system placeholder (always returns true)
- Limited asset type support (no audio streaming yet)
- UI system lacks advanced layout features
- No script debugging tools yet

**Security & Sandboxing**
- Script sandboxing not production-hardened
- Asset validation minimal
- No script resource usage monitoring
- Client-side script execution not fully isolated

## Potential Breaking Changes
- Asset packaging format modifications
- UI component API restructuring
- Event system overhaul for better error handling

## Credits 

- Created, designed and maintained by [@Meekiavelique](https://github.com/Meekiavelique) 
- Powered by EPI STUDIO - https://discord.gg/PvKeHzTwdU 
[![Banniereepistudio.jpg](https://i.postimg.cc/50yBMFKZ/Banniereepistudio.jpg)](https://postimg.cc/Fdtdy14Z)


