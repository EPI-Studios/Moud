# Moud

Moud is a game engine that runs inside Minecraft. You write a game in Luau, Moud runs it while
Minecraft is running, and you ship the result as a mod. Everything your code creates lives in a tree
of instances that the engine draws, collides and replicates for you.

Vanilla Minecraft features start switched off. Hunger, fall damage, block breaking, the hotbar and
the sky each have a setting in `place.toml`, and you turn on only the ones your game needs.

## A place

The game you write is called a place: a folder the engine reads when the game starts.

```
place/
  place.toml           the name, the entry points, and which vanilla features are on
  server/main.luau     the world, and everyone in it
  client/main.luau     the camera, the input, and this client's own body
  shared/              modules both sides require
  scenes/              .scene files laid out in the editor
```

`server/main.luau` builds the world and decides what happens in it:

```lua
local world = game.world

world:add("Part", {
    name = "floor",
    size = vec3(40, 1, 40),
    cframe = cframe(0, 64, 0),
    color = color(0.35, 0.42, 0.38),
    anchored = true,
})

game.players.joined:connect(function(player)
    player:spawn(vec3(0, 66, 0))
    player.character.humanoid.walkSpeed = 6
end)
```

[docs/getting-started.md](docs/getting-started.md) walks through a whole place from there, and
[docs/README.md](docs/README.md) indexes the rest.

## The editor

Moud has an editor in the game. It opens at the title screen with a project hub, and edits the
running place through the server: an explorer, properties, a viewport with camera, gizmos and
picking, an assets panel, an animation workspace over `.anim` clips, and plugins written in Luau.

## Repository layout

| Path | What it holds |
| --- | --- |
| `core/` | instances, classes, maths, assets, scenes: the engine with no Minecraft in it |
| `script/` | the scripting api, described once for every language, and the Luau adapter |
| `net/` | the packets the client and server send each other |
| `mod/` | the Fabric mod: rendering, physics, audio, input and the editor |
| `addons/revo/`, `addons/java/` | language addons, and the example of how to write one |
| `docs/` | the engine documentation |

## Other languages

A place is Luau by default, but the engine under it does not know Luau. Every global, instance,
signal and hook is described once and handed to whichever language runs the place. Moud picks the
language from the entry file it finds, so `server/main.rv` makes a Revo place. See
[docs/languages.md](docs/languages.md).

## Building

You need JDK 25 and these repos cloned next to this one, each built once: `Amnetic`, `Bkun`,
`Resona`, `polar`, `box3d-java`. Pass `-PamneticJar=/path/to/amnetic.jar` and the like if you keep
them somewhere else.

```
./gradlew build
```

The client and server run through Loom, with the run directory at `../Moud-run`:

```
./gradlew :mod:runClient -Pplace=/path/to/place
./gradlew :mod:runServer -Pplace=/path/to/place
```

`-Pplayer=name` names the player and `-Pjoin=host:port` joins a server on start.

The Revo addon builds its native library with [zig](https://ziglang.org) from a `revo` checkout next
to this repo. It is only needed if you build `addons:revo`.
