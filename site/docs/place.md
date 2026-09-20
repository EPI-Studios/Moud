# Places, modules, scenes and scripts

A place is the game you write: a folder of files that Moud reads when the game starts and reads again
every time you save. This page covers the file at its root that describes it, the folders inside it,
how one file loads another, the scene files the editor writes, how you ship the result, how a
server runs it, and the script instances you can put in the tree.

## place.toml

`place.toml` sits at the root of the place folder and says what the place is called, which files to
run, and which parts of vanilla Minecraft are switched on. A place without one still runs, from
`server/main.luau` and `client/main.luau`.

```toml
name = "Spinning Floor"
id = "spinning-floor"
version = "0.1.0"
maxPlayers = 8

[entry]
server = "res://server/main.luau"
client = "res://client/main.luau"
scene = "res://scenes/arena.scene"     # loaded under game.world before the server script runs

[features]
hunger = false
fallDamage = false
blockBreaking = false
chat = true
```

- `name` is what the place is called, `id` and `version` name the file **File > Export** builds, and
  `maxPlayers` is how many players the place takes.
- `[entry]` names the files Moud runs. `server` runs once on the server, `client` runs once inside
  each player's own game, and `scene` is loaded under `game.world` before the server script runs, so
  the server script finds the scene's instances already there.
- `[features]` is a list of switches, one per vanilla Minecraft feature.

An unknown setting is an error that lists the ones that exist.

`[features]` turns parts of vanilla Minecraft on or off. The names are camel case:

| Area | Features |
|---|---|
| World | `sky`, `clouds`, `fog`, `stars`, `weather`, `dayNightCycle`, `ambientLight`, `terrain`, `blockBreaking`, `blockPlacing`, `blockEntities`, `fluids`, `mobs`, `itemDrops` |
| Player | `hunger`, `healthRegen`, `fallDamage`, `drowning`, `fireDamage`, `vanillaMovement`, `inventory`, `crafting`, `xp`, `deathScreen` |
| Screen | `hotbar`, `healthBar`, `hungerBar`, `xpBar`, `crosshair`, `effectIcons`, `bossBar`, `chat`, `tabList`, `scoreboard`, `hand`, `pauseMenu`, `debugScreen`, `titleScreen` |
| Rendering and sound | `entityRendering`, `blobShadows`, `playerModel`, `nameTags`, `vanillaSounds`, `vanillaMusic` |
| Scripts | `httpRequests` |

`hand` is off by default. The first person arm still shows while it is off, because a body shows
it by default with `appearance.firstPerson`. Turning `hand` on shows the arm always, even for a body
set to `"none"` or a player with no body. Only `"body"` hides it then. See
[What you see of your own body](character.md#what-you-see-of-your-own-body).

`httpRequests` is not a Minecraft feature: it lets server scripts call out to websites. See
[Reaching websites](http.md).

What `[features]` says is what every client starts with. A client script changes seven of the screen
ones for itself while the place runs, with `ui.setCoreGuiEnabled`, and a reload puts them back the
way this table asked for them. See
[the core interface](game-and-screens.md#the-core-interface).

## Streaming

Streaming sends each client only the instances near it, so a large world does not have to fit in
every player's memory at once. It is on by default:

```toml
[streaming]
enabled = true    # default
radius = 256      # metres, default 256, between 32 and 4096
```

- The radius is measured from the player to each branch hanging off the root of the world. A branch
  further away than that is not sent, and is destroyed on that client until they come back. What is
  inside a branch goes with it, so a `Model` is sent or not sent whole.
- `alwaysRelevant` on a `Spatial` sends it whatever the distance. It is read on the branch itself,
  the one hanging off the root, not on something buried inside one. Use it for a skybox, a boss, or
  anything a far-away script looks at.
- Anything that is not a `Spatial` has no position, so it is always sent: values, remotes,
  containers, the [leaderboard](players.md#leaderstats).
- `enabled = false` sends everything to everyone. Do that for a small place where a client script
  reads the whole world.
- While you edit or playtest in the editor everything is sent, whatever this table says, so what the
  Explorer shows is the whole tree.

<!-- demo:place-streaming -->

> [!WARNING]
> A branch that comes back is sent again from scratch, so a client script that kept a reference to
> something far away is holding a destroyed instance. Look things up when you need them.

## Folders

The rest of the place is four folders, and which side reads a file depends only on which one it is
in:

```
place/
  place.toml
  server/     only the server reads these
  client/     only clients read these
  shared/     both sides read these
  scenes/     .scene files
```

## require

`require` loads another file and hands you back what it returns. There are two ways to name the file:

```lua
local palette = require("@shared/palette")
local same = require("res://shared/palette.luau")
```

- `@server/`, `@client/` and `@shared/` name those folders, and the extension can be left out. Use
  this form: it is the one Luau editors follow, so the module's own types reach the caller.
- A `res://` path starts at the place root and includes `.luau`. It cannot go up a folder: a `..`
  segment, as in `res://server/../secret.luau`, is refused, so a path never leaves the place. (This
  is about paths only; `..` joining strings works as usual.)
- Both forms name the same file, so they share one loaded module.
- A module runs **once per side**, and every caller gets the same value back.
- A module returns exactly one value.
- Two modules that require each other is an error: move what they share into a third.
- A module must not `task.wait` while loading. Start the waiting from a `task.spawn` inside it.
- Saving a module reloads the place, like any other file.
- `require` also takes a [ModuleScript](#modulescript) instance.

<!-- demo:place-require -->

> [!IMPORTANT]
> A client script may require `client/` and `shared/`, never `server/`. Server code never reaches a
> player's machine.

## Scenes

A scene file is a branch of the tree saved as JSON: classes, names, the properties that differ from
their defaults, tags, children, and references inside the branch. You lay a scene out in the editor
instead of writing it by hand, and a script loads it when it is needed.

```lua
-- server
local built = scene.load("res://scenes/arena.scene")          -- under game.world
local more = scene.load("res://scenes/props.scene", folder)   -- under another instance

scene.save({ game.world:find("arena") }, "res://scenes/arena.scene")

local text = scene.encode({ game.world:find("arena") })
local copies = scene.decode(text, game.world)
```

- `scene.load` parents what it builds under `game.world`, or under the instance you pass as the
  second argument.
- `scene.save` writes the instances you list to a file, and only writes `.scene` files.
- `scene.encode` gives you the same content as text, and `scene.decode` builds it again under the
  instance you name.

Scenes are what the editor reads and writes, which is why everything the engine can do is an
instance: a light, a post effect, a chat window, a zone and a collision group all save with the scene
they are in.

### Terrain

A scene's blocks live next to it: `scenes/arena.scene` uses `scenes/arena.polar`. A place from
before this keeps its `world.polar` at the root, and only the start scene uses it.

- **File > Import Minecraft World** turns a Java Edition world (any version, it is upgraded on the
  way) into the open scene's `.polar`. One scene holds one terrain, so importing replaces it; the
  old file goes to `.moud/backups`.
- Opening a scene in the editor swaps the terrain around you. Renaming, moving, duplicating or
  deleting a scene in Assets takes its `.polar` along.
- Only finished chunks are imported. Block entities such as chest contents are left out.

### In the editor

- Scenes open in tabs above the viewport. **Set as start scene** writes `[entry] scene`.
- **Ctrl+G** groups the selection. When everything selected has a position (parts, lights,
  attachments, other models) it goes into a `Model`. When the selection holds a gui or a script, or
  sits inside a viewport frame or a gui, it goes into a `Folder`. See [Physics](physics.md#models).
- While you edit, the scene is copied into `.moud/backups` every minute it changed, and the saved
  file is copied before each save. The last 20 are kept, **File > Scene Backups** restores one, and
  opening a scene with a newer autosave than its file offers to recover it.

## Exporting

**File > Export** builds `<id>-<version>.jar`: the engine without the editor, Amnetic, Bkun, Resona,
the language addons and the place. Put it in the `mods` folder of a Minecraft with Fabric Loader and
Fabric API, and launching goes straight into the place, unpacked into `moud-games/<id>`.

Or choose **Modrinth pack**: a `.mrpack` that Prism Launcher and the Modrinth App import as a
ready instance, with Fabric Loader and Fabric API already set up. It holds the same jar plus
Fabric API.

Before building, the export checks that the entry scripts and the start scene exist and that every
`res://` path written in scripts and scenes points at a file.

> [!NOTE]
> The engine runs Luau and physics through native libraries. The export lists the systems it found
> them for; a game only starts on those. Today the build only carries them for Linux x64.

## Running a server

A dedicated Minecraft server with Fabric and Moud runs a place when its Java command line names the
place folder with `-Dmoud.place=/path/to/place`.

- The server builds its world from the place, not from generated terrain. The level is empty void
  with the blocks of the start scene's `.polar` in it, and nothing else. A place with no terrain file
  gets an empty level.
- This only happens when the server creates its world. A world folder that already exists keeps
  whatever terrain it was made with: delete the folder `level-name` names in `server.properties`
  (`world` by default) and start again.
- Nobody is kicked for flying while a place runs. The vanilla server can not see parts, so a player
  standing on one looks to it like a player floating in the air, and Moud turns that check off.

### From the repository

Gradle starts a development server and clients that join it. The server needs a folder of its own
with an `eula.txt` holding `eula=true` and a `server.properties` with at least:

```properties
online-mode=false
server-port=25570
```

`online-mode=false` lets development clients in without a Microsoft account. Then:

```
./gradlew :mod:runServer -PrunDir=$HOME/moud/server -Pplace=$HOME/moud/arena
./gradlew :mod:runClient -PrunDir=$HOME/moud/alice -Pplace=$HOME/moud/arena -Pjoin=localhost:25570 -Pplayer=Alice
./gradlew :mod:runClient -PrunDir=$HOME/moud/bob -Pplace=$HOME/moud/arena -Pjoin=localhost:25570 -Pplayer=Bob
```

| | |
|---|---|
| `-PrunDir` | the folder the game or server runs in. It defaults to `Moud-run` beside the repository, so give each server and each client its own |
| `-Pplace` | the place folder, passed on as `moud.place` |
| `-Pjoin` | `host:port` of a server to join straight away, instead of opening the title screen |
| `-Pplayer` | the name the client plays under, so two clients on one machine are two players |

A relative `-PrunDir` or `-Pplace` counts from the repository's `mod` folder, so absolute paths are
the safer choice.

## Scripts as instances

A `Script` is an instance that holds server code, and a `LocalScript` is one that holds client code.
Each runs while it is in the tree and `enabled`, which lets you turn code on and off by moving it or
by writing one property.

```lua
world:add("Script", { name = "spinner", source = "res://server/spinner.luau" })
world:add("Script", { name = "hello", code = "print('hi from', script.name)" })
```

- `source` is a file under `server/` or `shared/` (`client/` or `shared/` for a `LocalScript`).
- `code` is the code itself and is used instead of `source` when set. It is never sent to a
  client.
- Inside the script, `script` is the instance. `local part = script.parent :: Part` gives it a type.
- In the editor, right-click an instance for **Add script**: it writes `server/scripts/<Name>.luau`
  from a template (empty, on touched, spin, kill on touch) and adds a `Script` pointing at it.
  Double-clicking a script opens it in Zed.
- Destroying or disabling the instance **stops everything it connected or scheduled**: its signal
  connections, its `task` threads and its tweens. Moving it keeps it running.
- A script inside `ServerStorage`, `ReplicatedStorage`, a `StarterPack` or
  `StarterCharacterScripts` does not run. Moving it in stops it like disabling it. See
  [Containers](containers.md).

<!-- demo:place-scripts -->

### ModuleScript

A `ModuleScript` is a module that lives in the tree, so a script can require it by where it is
rather than by a path. It never runs on its own.

```lua
-- a script inside game.world.lib
local weapons = require(script.parent.weapons)
local same = require(game.world.lib.weapons)
print(weapons.damage("sword"))   -- 12
```

```lua
-- weapons, a ModuleScript beside the script
local weapons = {}

function weapons.damage(name)
    return if name == "sword" then 12 else 4
end

return weapons
```

Both lines in the first sample reach the same module: `script.parent.weapons` walks up from the
script that is running, and `game.world.lib.weapons` names the same instance from the root.

- It has `source` and `code`, like a `Script`. `code` is used when set; `source` names a `.luau`
  file otherwise. One with neither is an error when required.
- It follows the rules of [require](#require): it runs **once per side**, returns one value, and a
  client may not require a `source` under `server/`.
- Every `ModuleScript` is loaded **once per instance**, whether it carries `code` or a `source`. Two
  modules pointing at the same file each run their own copy of it, and `require("@shared/weapons")`
  by path is a third. So `script` inside a module is always that `ModuleScript`, and `script.parent`
  is where it sits.
- `code` reaches clients only when the module can be theirs: inside a `LocalScript`,
  `ReplicatedStorage`, `StarterGui`, `StarterPlayerScripts` or `StarterCharacterScripts`, or when
  its `source` is a file under `client/` or `shared/`. Inside `ServerStorage` or
  `ServerScriptService` it never crosses, whatever else is true. Anywhere else it stays on the
  server. See [Containers](containers.md).
- Requiring anything else, like a `Folder`, is an error that says a `ModuleScript` was expected.
- In the editor, right-click an instance for **Add module script**: it writes
  `shared/modules/Module.luau` (`Module2.luau` and so on if that exists) and adds a `ModuleScript`
  pointing at it.
