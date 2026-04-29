# rapier_moud - native physics shim

rust crate that wraps `rapier3d` and exposes a C ABI consumed by
`physics-rapier/src/main/java/com/moud/physics/rapier/Rapier3D.java`

## local build

    ./gradlew physics-natives:build

produces `physics-rapier/src/main/resources/natives/<host>/librapier_moud.<ext>`
which gets bundled into the `physics-rapier` jar

## cross-platform builds

`.github/workflows/physics-natives.yml` builds these targets in CI and commits
the resulting binaries back into `physics-rapier/src/main/resources/natives`

- linux-x64    -> x86_64-unknown-linux-gnu
- linux-arm64  -> aarch64-unknown-linux-gnu
- windows-x64  -> x86_64-pc-windows-msvc
- macos-x64    -> x86_64-apple-darwin
- macos-arm64  -> aarch64-apple-darwin

triggers on push to main/rewrite touching `physics-natives/`, or run the
workflow manually from the actions tab
