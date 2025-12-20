
<img width="1944" height="794" alt="Moud Banner" src="https://github.com/user-attachments/assets/9523846b-b63c-4352-b227-7ae19a3d27d2" />


<p align="center">
  <img src="https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Powered%20by-Minestom-815db1?style=for-the-badge&logo=openjdk&logoColor=white" alt="Powered by Minestom" />
  <img src="https://img.shields.io/badge/Running%20on-Fabric-2C2C2C?style=for-the-badge&logo=openjdk&logoColor=white" alt="Running on Fabric" />
  <a href="https://moud.epistudios.fr/"><img src="https://img.shields.io/badge/Wiki-Documentation-4A90E2?style=for-the-badge&logo=gitbook&logoColor=white" alt="Wiki" /></a>
  <img src="https://img.shields.io/badge/Discord-online-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
</p>


<h1 align="center">Moud</h1>
<h3 align="center">TypeScript-First Minecraft Scripting Framework</h3>

<p align="center">
A modern, high-performance framework for building Minecraft experiences entirely in TypeScript. Write server logic, client modifications, and graphics code with a unified language, type-safe APIs, and real-time hot reload.
</p>

---

## Quick Start

```bash
npm install -g @epi-studio/moud-cli@latest
moud create my-game
cd my-game
moud dev
```

**Requirements:**
- **Server**: Java 21+
- **Client**: Minecraft 1.21.1 (Fabric) with Moud client mod
- **Development**: Node.js 20+, pnpm 9+

---

## What is Moud?

Moud is a scripting framework that brings modern TypeScript development to Minecraft. Built on [Minestom](https://github.com/Minestom/Minestom) for the server and [Fabric](https://fabricmc.net/) for the client, it use [GraalVM](https://www.graalvm.org/) to execute TypeScript/JavaScript at near-native performance on both sides.

### Key Advantages

- **TypeScript Everywhere** - Write both server and client code in TypeScript with full IntelliSense support
- **Unified Runtime** - GraalVM-powered execution on both client and server for consistent behavior
- **Type Safety** - Comprehensive type definitions for all APIs with compile-time checking
- **Hot Reload** - Live code updates during development without server restarts
- **Advanced Graphics** - Direct access to Veil rendering pipeline for shaders and post-processing
- **State Synchronization** - Reactive state management with automatic client-server sync
- **Physics Engine** - Integrated Jolt Physics for realistic simulations
- **Plugin System** - Extensible architecture supporting both TypeScript and Java plugins

---

## Core Features

### State Synchronization (Shared Values)

Automatically sync game state between server and client with reactive updates, conflict resolution, and per-player isolation.

```typescript
// create a shared value that syncs across server and client
const playerScore = api.sharedValue.create('score', 0);

// listen for changes
playerScore.onChange((newValue, oldValue) => {
    console.log(`Score changed: ${oldValue} → ${newValue}`);
});

// update from either side
playerScore.set(playerScore.get() + 10);
```

### Advanced Rendering

Powered by [Veil](https://github.com/foundry-mc/veil), Moud provides direct access to advanced rendering capabilities:

- **Custom Shaders** - Write GLSL shaders for unique visual effects
- **Post-Processing** - Bloom, blur, custom screen effects
- **Dynamic Lighting** - Runtime light creation and modification with color, intensity, and radius control
- **Particle System** - Keyframe-based particle effects with collision detection, billboarding, and light emission
- **Custom Models** - OBJ model loading with texture support and physics integration

### UI System

Server-driven UI framework with flexbox-inspired layout:

```typescript
// create an ui overlay from the server
const overlay = api.ui.createOverlay('game-hud', {
    type: 'container',
    layout: { anchor: 'top-center', padding: 10 },
    children: [
        {
            type: 'text',
            content: 'Welcome to Moud!',
            style: { color: '#FFD700', fontSize: 24 }
        },
        {
            type: 'button',
            content: 'Click Me',
            onClick: () => console.log('Button clicked!')
        }
    ]
});

player.ui.show(overlay);
```

### Camera Control

Full camera control system:

```typescript
// lock camera to specific position and rotation
player.camera.lock({
    position: { x: 0, y: 100, z: 0 },
    rotation: { yaw: 0, pitch: -45 },
    transition: { duration: 2000, easing: 'ease-in-out' }
});

// unlock with smooth transition
player.camera.unlock({ transition: { duration: 1000 } });
```

###  Physics Integration

Jolt Physics engine integration with automatic collision generation:

```typescript
// create physics-enabled object from model
const physicsObject = api.world.spawnModel({
    model: 'assets/models/crate.obj',
    position: { x: 0, y: 10, z: 0 },
    physics: {
        type: 'dynamic',
        mass: 10,
        friction: 0.5,
        restitution: 0.3
    }
});

// apply forces
physicsObject.applyForce({ x: 100, y: 0, z: 0 });
```

### Animation System

Player animation powered by [PlayerAnimationLib](https://github.com/ZigyTheBird/PlayerAnimationLibrary):

```typescript
// play animation with custom controls
player.animation.play('walk', {
    loop: true,
    speed: 1.2,
    blendTime: 0.3
});

// inverse kinematics for procedural animations
const ikChain = api.ik.createChain({
    segments: 3,
    length: 1.0,
    target: { x: 5, y: 2, z: 3 }
});
```


### Plugin System

Extend Moud with Java plugins using a clean, dsl-style API:

```java
public class ExamplePlugin extends Plugin {
    @Override
    public void onEnable(PluginContext context) {
        // spawn models, register commands, handle events
        world.spawnModel(
            "assets/models/structure.obj",
            new Vector3(0, 64, 0)
        );

        commands.register("hello", (player, args) -> {
            player.sendMessage("Hello from plugin!");
        });

        events.on(PlayerJoinEvent.class, event -> {
            // handle player join
        });
    }
}
```

---

## Architecture

Moud is organized as a monorepo containing both Java (Gradle) and TypeScript (pnpm workspace) components:

```
moud/
├── api/                    # core abstractions and data structures
├── network-engine/         # packet protocol
├── plugin-api/            # java plugin api
├── server/                # minestom-based server runtime
├── client-mod/            # fabric client mod
├── packages/
│   ├── sdk/              # typescript types definitions (sdk)
│   └── moud-cli/         # cli
└── example/
    ├── ts/               # typescript examples
    └── java-plugin/      # java plugin examples
```

### Technology Stack

**Server:**
- Minestom - Bare-Bone Minecraft server framework
- GraalVM  - High-performance JavaScript/TypeScript execution
- Jolt Physics  - Native physics engine (multi-platform)
- Jackson  - JSON serialization
- JavaFX  - Profiler UI
- ImGUI - Editor interface

**Client:**
- Minecraft 1.21.1 with Fabric Loader 0.17.2
- Veil - Advanced rendering capabilities
- PlayerAnimationLib - Player animation system
- GraalVM - Client-side script execution

**Development:**
- TypeScript 5.0+ - Type-safe scripting
- esbuild - Fast bundling and optimization
- Node.js 20+ & pnpm 9+ - Package management


---

## Development
### Setup

```bash
# clone repository
git clone https://github.com/EPI-Studios/Moud.git
cd Moud

The rest will be in the documentation
```
### Profiler

Enable performance profiling with the `--profile-ui` flag:

```
java -jar moud-server.jar --profile-ui
```


The profiler window shows:
- Script execution times (timeouts, intervals, events)
- System tick times
- Network packets
- Performance bottlenecks

---

## Project Status

### Working

- Full TypeScript server/client scripting with GraalVM execution
- Shared Values state synchronization with conflict resolution
- Component-based UI system with server-driven overlays
- Asset packaging, streaming, and hot reload
- Event system with custom client-server events
- Dynamic lighting with runtime creation/modification
- Custom camera controls with smooth transitions
- 3D cursor system with raycast detection
- Physics integration (Jolt Physics) with model collision
- Particle system with keyframe animation
- IK chains for procedural animation
- Plugin system with Java and TypeScript support

### In Development

- Script debugging with source maps
- Advanced performance profiling tools
- Production deployment workflows

###  Planned
- Assets streaming 
- GLB/GLTF rigging support [#20](https://github.com/EPI-Studios/Moud/issues/20)
- Server instancing for scalability
- Advanced Veil integration (custom shaders, render layers)


## Versioning

Moud uses a multi-version strategy:

- **Release Version** (`0.8.0`): Stored in `VERSION` file, synced across all modules
- **Network Protocol** (v1): Independent versioning in `MoudProtocol.java` for wire compatibility
- **Plugin API** (v1.0): Separate version in `PluginApi.java` for plugin compatibility

Version synchronization is enforced by CI through `scripts/sync-version.mjs`.



## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository and create your branch from `develop`
2. Set up your development environment (JDK 21, Node.js 20+, pnpm 9+)
3. Make your changes following the coding style and conventions
4. Ensure `./gradlew build` and `pnpm build` succeed
5. Commit using [Conventional Commits](https://www.conventionalcommits.org/) specification
6. Open a Pull Request to the `develop` branch

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed contribution guidelines.



## Documentation

- **Wiki**: [https://moud.epistudios.fr/](https://moud.epistudios.fr/)
- **API Reference**: TypeScript definitions in `packages/sdk/src/index.ts`



## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 EPI Studios

---

## Credits & Acknowledgments

Moud is nothing without these projects. Special thanks to:

- **[Minestom](https://github.com/Minestom/Minestom)** - For the incredibly powerful and modern Minecraft server foundation
- **[GraalVM](https://github.com/oracle/graal)** - For JavaScript & TypeScript execution on the JVM 
- **[FabricMC](https://github.com/FabricMC/fabric)** - For the client-side modding toolchain and API
- **[Veil](https://github.com/foundry-mc/veil)** - For advanced rendering capabilities 
- **[PlayerAnimationLib](https://github.com/ZigyTheBird/PlayerAnimationLibrary)** by [@ZigyTheBird](https://github.com/ZigyTheBird) - For the client-side animation system
- **[Jolt Physics JNI](https://github.com/stephengold/jolt-jni)** - For the high-performance physics engine 
- **[mdoc](https://github.com/Meekiavelique/mdoc)** - For providing the foundation for the documentation website

---

<p align="center">
  <strong>Created by <a href="https://github.com/Meekiavelique">@Meekiavelique</a></strong><br>
  <strong>EPI STUDIO</strong> - <a href="https://discord.gg/PvKeHzTwdU">Join our Discord</a>
</p>

<p align="center">
  <a href="https://postimg.cc/Fdtdy14Z">
    <img src="https://i.postimg.cc/50yBMFKZ/Banniereepistudio.jpg" alt="EPI STUDIO Banner" />
  </a>
</p>
