# Moud vNext (new rewrite)

This directory is a clean-slate rewrite scaffold intended to be developed like a new repository while keeping the legacy monorepo as reference.

## Build

- `../gradlew -p next test`

## Modules (current)

- `core`: version constants and shared primitives (host-agnostic).
- `core.scene`: minimal Godot-like node tree (`Node`, `SceneTree`) — the direction for replacing legacy “proxy” surfaces.
- `net`: first message set + binary encoding, transport contract, and session handshake in one module.
- `server-minestom`: Minestom server bootstrap (sessions per player).
- `editor-client`: standalone Miry demo client (in-memory transport) to exercise snapshots + ops (only included when `../../../fliffel` exists).

## Run server (Minestom)

- `../gradlew -p next :server-minestom:run`

Notes:
- Uses plugin channel `moud:engine` with `TransportFrames` envelope (lane + payload).
- Scene editing is supported over `SceneSnapshotRequest/SceneSnapshot` and `SceneOpBatch/SceneOpAck`.

## Run local editor demo (no Minecraft)

- `../gradlew -p next :editor-client:run`

## Next steps

- Replace `byte[]` payloads with pooled buffers (no per-message alloc).
- Add feature negotiation + compression selection to the handshake.
- Implement `transport-fabric` (CustomPayload), keeping `core/net` Minecraft-free.
- Miry overlay inside `client-mod` (only enabled when connected to a Moud server that speaks `moud:engine`).
