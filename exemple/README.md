This folder is a self-contained MOUD project used to smoke-test engine/editor features.

**How to run the server using this project**

- From the repo root:
  - `MOUD_PROJECT_ROOT=exemple ./gradlew --gradle-user-home .gradle-home :server-minestom:run`

**What this project tests**

- Jolt-backed `CharacterBody3D` collisions against `CSGBox` / `CSGBlock` (if Jolt native is available)
- `WorldEnvironment` custom sky + clouds (Veil dynamic shaders)
- `.moudmat` materials + `@expose` shader params (Inspector edits update the material asset)
- Material preview thumbnail in Inspector
- Scene instancing via `SceneInstance3D`

**Jolt native note**

If the server prints `Jolt disabled` / `UnsatisfiedLinkError`, your runtime is missing the platform-native Jolt binary.

- Option A (recommended): let Gradle resolve the `jolt-jni-*-DebugSp` runtime artifacts
- Option B: point MOUD at an existing native file: `MOUD_JOLT_NATIVE=/full/path/to/libjoltjni.so`
- Option C: MOUD will attempt an auto-download from Maven Central when needed

**Scenes**

- `scenes/showcase.moud.scene`: main “everything” scene
- `scenes/prefab_room.moud.scene`: instanced by `showcase`
