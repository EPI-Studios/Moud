# Containers

A container is a folder with a rule attached to it. Where you put an instance normally only decides
where you find it again, but inside a container it also decides three things: whether players are
sent that instance, whether the scripts inside it run, and whether its parts are in the world.

You use containers to keep things the game is going to need later without having them in the game
yet. A coin you clone once every three seconds, a map you load at the start of a round, a table of
prices both sides read, the tools a player starts with: each of those wants to exist before it is
used, and none of them should be standing in the world in the meantime.

None of them exists until you add it. Insert one from the Explorer in the editor, or add it from a
server script. An added instance is named after its class, so `game.world:add("ServerStorage")` is
found again as `game.world.ServerStorage`.

## What each one does

| Container | Sent to players | Scripts inside run | Parts inside are in the world | Also |
|---|---|---|---|---|
| `ServerStorage` | no | no | no | |
| `ServerScriptService` | no | `Script`s do | no | |
| `ReplicatedStorage` | yes | no | no | |
| `StarterPack` | yes | no | no | its tools are copied into every new body's backpack |
| `StarterCharacterScripts` | yes | no | no | its children are copied into every new body |
| `StarterGui` | yes | yes | yes | an ordinary container |
| `StarterPlayerScripts` | yes | yes | yes | an ordinary container |
| `Backpack` | yes | yes | no | every body has one, see [Tools](tools.md) |

The rule holds for everything below the container, however deep. A part ten levels down inside a
`ServerStorage` is as absent from the world as one sitting directly in it.

Here is what each of the three columns means:

- **Not sent to players** means a client's mirror never holds it. A client cannot find it, require
  it or read its `code`. While you edit, the editor is sent it too, so you can see and change what
  is inside.
- **Scripts do not run** means a `Script` or `LocalScript` inside is only kept, on either side. A
  copy moved out of the container runs.
- **Not in the world** means a part inside is not drawn, does not collide, does not fall, is not
  found by casts and overlaps, and never starts a touch. A zone, a prompt, a click detector, a light
  or a particle emitter inside does nothing either. Nor does a `ScreenGui`, `BillboardGui` or
  `SurfaceGui` draw, or a `Sound` play. Take a copy out and the copy does all of it.

Everything else still works on what is inside: finding, reading and setting properties, attributes,
tags, `clone` and `require`.

<!-- demo:containers-rules -->

A `ModuleScript`'s `code` has a rule of its own on top of this: it only reaches clients from inside
a `LocalScript`, `ReplicatedStorage`, `StarterGui`, `StarterPlayerScripts` or
`StarterCharacterScripts`, or when its `source` is a file under `client/` or `shared/`. See
[ModuleScript](place.md#modulescript).

<!-- demo:containers-module -->

## ServerStorage

`ServerStorage` is where the server keeps what it builds from: a coin, an enemy, a map it loads
later. Nobody else is sent it, and nothing in it runs or stands anywhere. Taking a copy out puts the
copy in the world.

```lua
-- server/main.luau
local storage = game.world:add("ServerStorage")

local coin = storage:add("Part", {
    name = "coin",
    size = vec3(0.6, 0.6, 0.1),
    color = color(1, 0.8, 0.2),
    anchored = true,
    collides = false,
})
coin:add("Script", { name = "collect", source = "res://server/coin.luau" })

task.every(3, function()
    local copy = coin:clone(game.world) :: Part
    copy.position = vec3(random.range(-10, 10), 66, random.range(-10, 10))
end)
```

```lua
-- server/coin.luau
local coin = script.parent :: Part

coin.touched:connect(function(other)
    if other:firstAncestorOfClass("Character") then
        coin:destroy()
    end
end)
```

- The coin in the storage is a template. No player is sent it, it is not drawn, and the `collect`
  script inside it never runs.
- `coin:clone(game.world)` makes a copy and parents it to the world in one call. That copy is sent
  to every player, drawn, touched, and runs its own copy of the script.
- `script.parent` inside `coin.luau` is the copy the script came with, so each coin listens for its
  own `touched` and destroys itself.

<!-- demo:containers-clone -->

`coin:clone()` without a parent puts the copy beside the original, which is still inside the storage,
so the copy is as inert as the coin it came from. Pass the parent you want, or set `parent` straight
afterwards.

A scene saves its containers like anything else, so the coin is usually built in the editor and only
cloned from the script: `game.world.ServerStorage.coin:clone(game.world)`.

## ServerScriptService

`ServerScriptService` is where server scripts go. A `Script` inside runs as it would anywhere, and
players are never sent it, so its `code` stays on the server. Parts inside are not in the world.

A `ModuleScript` that only the server requires can go here or in `ServerStorage`, for the same
reason.

## ReplicatedStorage

`ReplicatedStorage` holds what both sides need but neither should run or see standing in the world:
templates a client copies for itself, and `ModuleScript`s. `require` takes a `ModuleScript` wherever
it is, so one in `ReplicatedStorage` is required by the server and by every client.

```lua
-- prices, a ModuleScript in game.world.ReplicatedStorage
local prices = {
    sword = 50,
    shield = 30,
}

function prices.canAfford(coins: number, item: string): boolean
    local price = prices[item]
    return typeof(price) == "number" and coins >= price
end

return prices
```

The server requires it to decide whether a purchase goes through:

```lua
-- server/main.luau, with buy and coins from Talking across the boundary
local prices = require(game.world.ReplicatedStorage.prices)

buy.onServerInvoke = function(player, item)
    if not prices.canAfford(coins[player.id] or 0, item) then return false end
    coins[player.id] -= prices[item]
    return true
end
```

The client requires the same module to fill a label with the same number:

```lua
-- client/main.luau
task.spawn(function()
    local storage = game.world:waitForChild("ReplicatedStorage")
    local prices = require(storage:waitForChild("prices"))
    label.text = "Sword: " .. prices.sword
end)
```

- The client waits for both the container and the module with `waitForChild`, because the server
  builds them and the client's mirror fills in after it connects. `waitForChild` yields, which is
  why it runs inside a `task.spawn`.
- The module runs once on the server and once on each client, as [require](place.md#require) says.
- The server still checks the price itself. A client holding the same module could be lying about
  what it can afford.

A client that clones from `ReplicatedStorage` makes a copy only it has. See
[Talking across the boundary](talking.md).

## StarterGui

`StarterGui` is a place for guis. It is an ordinary container: a `ScreenGui` inside draws for every
player, and a `LocalScript` inside runs on each client.

> [!IMPORTANT]
> `StarterGui` is not copied into each player when they spawn. There is no `PlayerGui`, no player
> owns a copy, and nothing in it resets on respawn.

What that means when you write one:

- There is one gui, the one in `StarterGui`, and every client draws its mirror of it.
- A `LocalScript` changing a label changes it on that client only, because what a client changes
  stays on that client. Each player still sees their own text.
- A server script changing a label changes it for everyone.
- Nothing resets when a body dies. Put it back yourself on `game.players.spawned` if you want that.

## StarterPlayerScripts

`StarterPlayerScripts` is a place for `LocalScript`s that belong to the player rather than to a part.
It is an ordinary container: each client runs a `LocalScript` inside, exactly as it would one
anywhere else. It is there to keep them together.

## StarterCharacterScripts

`StarterCharacterScripts` holds children that are copied into every new body a player gets: on the
first spawn and on each respawn, before `game.players.spawned` fires. The originals inside it do not
run. Bodies without a player get nothing.

```lua
-- server/main.luau
local scripts = game.world:add("StarterCharacterScripts")
scripts:add("Script", { name = "limp", source = "res://server/limp.luau" })
```

```lua
-- server/limp.luau
local body = script.parent :: Character
local humanoid = body.humanoid

humanoid.healthChanged:connect(function()
    humanoid.walkSpeed = if humanoid.health < 30 then 3 else 6
end)
```

Every player who spawns gets their own copy of `limp`, whose `script.parent` is their own body, so
each player slows down on their own wounds. The copy dies with the body, so it stops with it, and the
next respawn brings a fresh one.

A `LocalScript` copied into a body runs on every client, not only the one wearing the body. Check
whose body it is first:

```lua
-- client/sprint.luau, copied into every body
local body = script.parent :: Character
if body ~= game.players:me() then return end
```

`game.players:me()` is the local player's own body, so the comparison stops the script early on every
client except the one that owns it.

## StarterPack

`StarterPack` holds tools that are copied into every new body's backpack. The tools inside it are not
in the world and cannot be picked up, and their scripts do not run. See
[Tools](tools.md#a-starter-pack).

## Backpack

A `Backpack` holds the tools a body is carrying, and every body has one. Their `Handle` is not drawn
and not simulated, and nobody picks them up. Scripts inside carried tools still run, so a tool can
listen for `equipped` while it waits in the backpack. See [Tools](tools.md).
