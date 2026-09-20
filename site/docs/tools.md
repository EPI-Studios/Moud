# Tools

A `Tool` is an instance a body carries and holds: a sword, a torch, a wand. Tools sit in a body's
`backpack`, the player sees them in Minecraft's hotbar, and the slot they select is the tool the body
holds. Clicking with a held tool fires its `activated` signal, which is where you write what the tool
does.

```lua
-- server
local sword = body.backpack:add("Tool", { name = "sword", item = "minecraft:iron_sword" })
body.humanoid:equipTool(sword)
```

- `body.backpack:add("Tool", { ... })` creates the tool and parents it to that body's backpack, so
  that body carries it and nobody else does.
- `item` is the Minecraft item drawn in the hotbar slot and in the hand.
- `humanoid:equipTool(sword)` puts the tool in the body's hand straight away, instead of waiting for
  the player to select its slot.

## Carried and held

Every body has a `backpack`. Where a tool sits in the tree is what says what the body does with it:

| The tool is in | |
|---|---|
| `body.backpack` | carried |
| `body` itself | held, or equipped. A body holds one tool at a time |
| anywhere else | lying in the world, or kept somewhere by the place |

A carried tool is out of the world: its `Handle` is not drawn, has no physics and is not found by
casts. Scripts inside it still run, so a tool can sit in the backpack waiting for `equipped`.

Equipping moves the tool out of the backpack into the body. Unequipping moves it back.

> [!IMPORTANT]
> Use `humanoid:equipTool` rather than setting `parent`. A server script that moves the tool itself
> fires no events, and a player's hotbar selection puts back what it points at on the next tick.

<!-- demo:tools-hotbar -->

## A tool's properties

Each property has a default that already works, so a tool created with only a `name` and an `item` is
usable as it stands. You set the rest when you want the tool to behave differently from that:

| Property | Default | |
|---|---|---|
| `enabled` | true | off: clicking with the tool does nothing, and neither does `tool:activate()` |
| `item` | `""` | the Minecraft item shown in the hotbar and in the hand, like `minecraft:iron_sword`. A stick when empty or unknown |
| `toolTip` | `""` | the name shown in the hotbar. The tool's `name` when empty |
| `grip` | no turn, no offset | where the hand holds the `Handle`, in the Handle's own frame |
| `requiresHandle` | false | the tool can only be equipped while it has a part named `Handle` |
| `canBeDropped` | true | the drop key leaves the tool in the world |
| `manualActivationOnly` | false | clicking does not activate the tool; only `tool:activate()` does |

`enabled` and `manualActivationOnly` both stop a click from doing anything, and they differ in what
else they stop. With `enabled` off the tool is dead: even a script calling `tool:activate()` gets
nothing. With `manualActivationOnly` on, the player's click is ignored and your script's
`tool:activate()` still works, which is how you drive a tool from a cooldown or a menu instead of
from the mouse.

<!-- demo:tools-activation -->

A tool has four signals:

| Event | Fires with | |
|---|---|---|
| `equipped` | the tool | when it moves into a body |
| `unequipped` | the tool | when it leaves a body, into the backpack or the world |
| `activated` | the tool | when the holder presses the left mouse button, or on `tool:activate()` |
| `deactivated` | the tool | when the holder lets go, or on `tool:deactivate()` |

`tool:activate()` and `tool:deactivate()` fire the events on the side that calls them. They do
nothing while the tool is not held or `enabled` is off. Call them from a server script when the body
holding the tool has no player to click for it.

## The hotbar

A player sees the tools they carry in Minecraft's hotbar. Each tool takes the first free slot, up to
nine, and keeps that slot until it leaves. The one they hold stays in its slot too. A Minecraft item
already in that slot is replaced.

- Selecting a slot with the number keys or the mouse wheel equips its tool.
- Selecting an empty slot unequips.
- A tool's item cannot be moved out of the hotbar. A copy anywhere else in the inventory is removed.

With `hotbar = false` in place.toml the bar is hidden, and the number keys still pick tools. Use that
when you draw your own selection interface and want the vanilla bar out of the way.

## The Handle

A part named `Handle` directly inside a tool is what the hand holds. While the tool is equipped, the
engine moves the Handle to the right hand's grip every tick. The server does it for every body, with
or without a player, and unanchors the Handle; every client does it too.

```lua
local sword = body.backpack:add("Tool", { name = "sword", grip = cframe(0, -0.5, 0) })
sword:add("Part", { name = "Handle", size = vec3(0.1, 1.2, 0.1), color = color(0.8, 0.8, 0.85) })
```

`grip` is where the hand holds the Handle, in the Handle's own space. `cframe(0, -0.5, 0)` holds the
part half a metre below its middle, so most of it stands out of the fist. Turn it with `cframe.angles`
until the tool points the way you want.

<!-- demo:tools-grip -->

Only the Handle is moved. Other parts in the tool stay where they are, so build a tool from one
Handle or [weld](physics.md#welds) the rest to it.

A tool without a Handle still works: it sits in the hotbar, it is held, and it activates. It just has
nothing to hold but its `item`.

A player's body draws the `item` in its hand as well, because the tool really is in their hotbar. To
show only the Handle, take the item out of the hand while the tool is held:

```lua
-- server, in a Script inside the tool
local tool = script.parent :: Tool
local holder: Character? = nil

tool.equipped:connect(function()
    holder = tool.parent :: Character
    holder.rightItemOverride = "minecraft:air"
end)

tool.unequipped:connect(function()
    if holder then holder.rightItemOverride = "" end
    holder = nil
end)
```

Setting `rightItemOverride` to `"minecraft:air"` empties the drawn hand, and setting it back to `""`
lets the body draw whatever it is really holding again. The script keeps the body in `holder` because
by the time `unequipped` fires the tool's parent is no longer that body.

## Clicking

While a player holds a tool, pressing the left mouse button fires `activated` and letting go fires
`deactivated`. They fire in the server's scripts and in the `LocalScript`s of the player holding it.
Other players' clients do not see the click.

Nothing happens while a Minecraft screen, like the inventory or the chat, is open, or while the
tool's `enabled` is off or its `manualActivationOnly` is on.

The click is the tool's alone. While a tool is held, Minecraft never sees the button: the arm does
not swing, no block is broken and nothing under the crosshair is hit. Swing the arm yourself with an
animation, as the sword below does.

## A sword

The sword is a Handle, a server `Script` that hurts what it hits, and a `LocalScript` that swings the
arm.

```lua
-- server/main.luau
local pack = game.world:add("StarterPack")

local sword = pack:add("Tool", {
    name = "sword",
    toolTip = "Sword",
    item = "minecraft:iron_sword",
    grip = cframe(0, -0.5, 0),
})
sword:add("Part", { name = "Handle", size = vec3(0.1, 1.2, 0.1), color = color(0.8, 0.8, 0.85) })
sword:add("Animation", { name = "swing", animationId = "res://animations/swing.anim" })
sword:add("Script", { name = "damage", source = "res://server/sword.luau" })
sword:add("LocalScript", { name = "swing", source = "res://client/swing.luau" })
```

Everything the sword needs is inside the tool, so the copy in each player's backpack carries its own
Handle, its own animation and its own scripts.

The server script casts a short ray from the holder's head along where they look. It runs in every
copy of the sword, and only the copy that is held fires `activated`.

```lua
-- server/sword.luau
local tool = script.parent :: Tool

tool.activated:connect(function()
    local body = tool.parent :: Character
    if not cooldown:ready(body, "sword", 0.4) then return end

    local hit = game.world:raycast(body.head.position, body:lookDirection(), 3, { exclude = { body } })
    if not hit then return end

    local target = hit:firstAncestorOfClass("Character") :: Character?
    if target then
        target.humanoid:takeDamage(4)
    end
end)
```

- `tool.parent` is the body holding the tool, because a held tool is parented to the body itself.
- `exclude = { body }` keeps the ray from hitting the swinger's own limbs at point blank.
- A ray hits limbs, so the part it returns is inside the body it struck. `firstAncestorOfClass` walks
  up from that limb to the `Character` it belongs to.

See [Queries](queries.md) for the other casts and the lag compensation a fast game wants.

The swing, as a `.anim` file:

```json
{
  "length": 0.3,
  "priority": 2,
  "keyframes": [
    { "time": 0,    "poses": { "rightArm": { "angles": [150, 0, 0], "easing": "quad", "direction": "out" } } },
    { "time": 0.15, "poses": { "rightArm": { "angles": [40, 0, -20], "easing": "sine" } } },
    { "time": 0.3,  "poses": { "rightArm": { "angles": [0, 0, 0] } } }
  ]
}
```

The `LocalScript` loads it into the holder's animator the first time the sword is used, and plays it
on every click:

```lua
-- client/swing.luau
local tool = script.parent :: Tool
local swing = tool:find("swing") :: Animation
local track: AnimationTrack? = nil

tool.activated:connect(function()
    local body = tool.parent :: Character
    local playing = track or body.animator:loadAnimation(swing)
    track = playing
    playing:play(0.05)
end)
```

The `track` variable keeps the loaded track so the second click reuses it instead of loading the
animation again, and `play(0.05)` fades the swing in over five hundredths of a second.

> [!NOTE]
> A track a `LocalScript` loads is only seen by that client. It answers the click at once, with no
> round trip, and other players see nothing. To show the swing to everyone, load and play it in the
> server script instead. See [Animations](animation.md).

## A starter pack

A `StarterPack` holds the tools every body starts with. When a player gets a new body, every tool in
every `StarterPack` in the scene is copied into its backpack, scripts and all. That happens on the
first spawn and again on each respawn, since a new body starts with an empty backpack.

```lua
-- server
local pack = game.world:add("StarterPack")
pack:add("Tool", { name = "torch", item = "minecraft:torch" })
pack:add("Tool", { name = "map", item = "minecraft:filled_map", toolTip = "Map" })
```

Put it in a scene in the editor or add it from a script. Only tools directly inside it are copied.
Adding a tool to a pack later does not reach bodies that already exist, so a tool you want an
existing player to have goes into their backpack directly.

A pack is a [container](containers.md): its tools are not in the world, so their Handles are not
drawn and nobody walks into one to pick it up, and the scripts inside them do not run. They run in
each copy instead.

The pack is copied before `game.players.spawned` fires, so a spawned handler already finds the tools:

```lua
game.players.spawned:connect(function(player)
    local body = player.character :: Character
    local torch = body.backpack:find("torch") :: Tool?
    if torch then torch.toolTip = player.name .. "'s torch" end
end)
```

That is where you change a copy for one player. The tool in the pack keeps its own `toolTip`, and the
next player to spawn gets a fresh copy of it.

## Dropping and picking up

Pressing the drop key while holding a tool takes it out of the hand:

- With `canBeDropped` on and a `Handle`, the tool is put in the world, its Handle 2 metres in front
  of the body. `unequipped` fires.
- With `canBeDropped` off, nothing is dropped and the tool stays in the hand.
- A tool without a `Handle` is never dropped: the key does nothing.

A player whose body walks within 2.5 metres of a tool's Handle picks it up into their backpack. That
counts any tool with a Handle that is not held, not carried and not in a container, so a tool placed
in a scene is picked up the same way. A dead body picks up nothing, and neither does a body without a
player.

A dropped tool waits before it is picked up again: one second for anyone, four seconds for the player
who dropped it. Without that it would go straight back into the hand it left.

The hotbar and the inventory stay in step with what the body carries. A tool's item cannot be thrown
out of the inventory screen, and dropping never leaves a copy behind or takes a tool away twice.

A dropped Handle that collides falls and lands. One with `collides = false` stays in the air where it
was left.

<!-- demo:tools-pickup -->

To drop a tool from a script, move it into the world and place its Handle:

```lua
-- server
local function drop(body: Character, tool: Tool)
    body.humanoid:unequipTools()
    tool.parent = game.world
    local handle = tool:find("Handle") :: Part
    handle.position = body.position + body:lookDirection() * 4 + vec3(0, 1, 0)
end
```

> [!WARNING]
> A tool put down this way has no waiting time, so place its Handle out of the body's 2.5 metres or
> it is picked straight back up. The four metres in front used above clears that.

## Equipping from a script

`humanoid:equipTool(tool)` and `humanoid:unequipTools()` run in server scripts. A `LocalScript`
calling either gets an error.

```lua
-- server
game.players.spawned:connect(function(player)
    local body = player.character :: Character
    local sword = body.backpack:find("sword") :: Tool?
    if sword then body.humanoid:equipTool(sword) end
end)
```

- `equipTool` puts away whatever the body holds, then holds the tool. It fires `unequipped` on the old
  one and `equipped` on the new one.
- The tool can be anywhere: in the backpack, in the world, in another body's backpack. It is moved
  into the body.
- A tool with `requiresHandle` and no Handle is not equipped, and nothing fires.
- `unequipTools` moves the held tool back to the backpack. It does nothing when nothing is held.
- Each fires its events once, however the hotbar looked when it was called.
- On a player's body, `equipTool` moves the hotbar selection to the tool's slot. `unequipTools`
  leaves the selection where it is and the body empty-handed, even when all nine slots are full; the
  player equips again by picking another slot.

Giving a tool is putting one in a backpack:

```lua
local sword = pack:find("sword") :: Tool
sword:clone(body.backpack)
```

`clone` copies the tool and everything inside it, so the new backpack gets its own Handle and its own
scripts. The tool in the pack is untouched.

A body without a player holds tools too. It has no hotbar and no clicks, so a script equips the tool
and calls `tool:activate()` for it.

```lua
-- server
local guard = game.world:add("Character", { name = "guard", cframe = cframe(4, 65, 0) })
local spear = guard.backpack:add("Tool", { name = "spear" })
spear:add("Part", { name = "Handle", size = vec3(0.1, 2, 0.1) })
guard.humanoid:equipTool(spear)

task.every(2, function()
    spear:activate()
end)
```

Every two seconds the guard's spear fires `activated`, and any script inside the tool runs the same
code it would run for a player's click.

## While editing

Tools do nothing while you edit a scene: the hotbar is not updated, clicks do not activate, Handles
are not held and nothing is picked up. It all starts when the place plays.
