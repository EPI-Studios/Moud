# Zones, touches, tags, prompts and clicks

This page covers the ways a place notices something without you checking for it every frame: a part
that reports what runs into it, a region that knows what stands in it, a label you attach to
instances so you can find them all at once, a "press E" prompt, and a part a player can click. The
last two sections cover watching a pair of instances and turning a distance into a strength.

## touched and touchEnded

Every part has a `touched` and a `touchEnded` signal. `touched` fires when another part starts
overlapping it, and `touchEnded` when that part stops. Both hand you the other part:

```lua
pad.touched:connect(function(other) end)
pad.touchEnded:connect(function(other) end)
```

Touches are worked out once a tick, and only for parts that something listens on, so a part with no
connection costs nothing. A trigger is an ordinary part with `collides = false` and
`transparency = 1`: players walk through it and never see it, and it still reports what went past.
`canTouch = false` opts a part out.

> [!NOTE]
> A body walking along the edge of a part can start and end a touch several times a second, because
> each tick is worked out on its own. Use a `Zone` when you need a steady answer to "is this player
> in here".

## Zones

A `Zone` is an instance that knows what is inside a shape you give it. It asks whether a point is
inside that shape, which is cheaper and steadier than `touched`: a body standing on the edge does
not flicker in and out.

```lua
local safe = world:add("Zone", {
    shape = "box",                 -- box, sphere, cylinder, polygon
    size = vec3(20, 6, 20),        -- sphere: diameter in x. cylinder: diameter in x, height in y
    cframe = cframe(0, 65, 0),
    trackPlayers = true,           -- bodies players wear
    trackTag = "",                 -- and anything with this tag
    trackClass = "",               -- and anything of this class
    cooldown = 0.5,                -- seconds before the same thing can enter or leave again
    priority = 0,
})
safe.entered:connect(function(thing) end)
safe.left:connect(function(thing) end)

safe:players()        -- the bodies inside
safe:occupants()      -- everything inside
safe:contains(position)
```

- `shape` and `size` make the region. A box is 20 by 6 by 20 metres here, centred on the `cframe`.
- The three `track` properties say what the zone watches. With `trackPlayers = true` it follows the
  bodies players wear; fill `trackTag` or `trackClass` and it follows those as well.
- `cooldown` is how long the same thing has to wait before it can enter or leave again, which keeps
  a player hopping over the boundary from firing the signals repeatedly.
- `entered` and `left` fire with the thing that moved. `players()`, `occupants()` and `contains()`
  answer the same question at the moment you ask it, which is what you want inside a loop or when a
  player presses a button.

<!-- demo:zone -->

**Polygon zones** use their `Attachment` children as the outline on the zone's floor, and are as
tall as `size.y`:

```lua
local yard = world:add("Zone", { shape = "polygon", size = vec3(0, 8, 0) })
yard:add("Attachment", { cframe = cframe(0, 0, 0) })
yard:add("Attachment", { cframe = cframe(12, 0, 0) })
yard:add("Attachment", { cframe = cframe(6, 0, 10) })
```

The three attachments are the corners of a triangle, and `size.y` of 8 makes the zone eight metres
tall. The x and z of `size` are unused, because the outline decides the footprint.

**Where zones overlap**, `game.zones:at(position)` lists every zone there with the winner first:
highest `priority`, then the smallest.

**A zone can change channels and sound** without a script listening to it:

```lua
world:add("Zone", { size = vec3(10, 5, 10), textChannel = world:find("tavern"), soundId = "res://music/tavern.ogg", volume = 0.6 })
```

- A player inside is put in `textChannel`, and taken out when they leave.
- `soundId` loops on that player's client while the zone is the winning one where they stand.

## Tags

A tag is a name you attach to an instance so you can find every instance wearing it later. You use
one where a class or a parent will not do, such as marking twenty scattered parts as lava.

```lua
lava:addTag("lava")
lava:hasTag("lava")  lava:removeTag("lava")  lava:getTags()

for _, part in game.tags:tagged("lava") do end
game.tags:added("lava"):connect(function(instance) end)
game.tags:removed("lava"):connect(function(instance) end)

world:byTag("lava")        -- only the tagged instances under this one
```

- `addTag`, `removeTag`, `hasTag` and `getTags` work on any instance.
- `game.tags:tagged(name)` gives every instance with the tag, wherever it is.
- `game.tags:added` and `game.tags:removed` fire when an instance gains or loses the tag, so a
  script can set a part up the moment it is marked instead of scanning for new ones.
- `world:byTag(name)` narrows the search to the instances under the one you called it on.

Tags replicate, so a tag the server adds reaches every client.

> [!IMPORTANT]
> A client can only tag what it owns or made itself. Tagging anything else belongs on the server.

## Proximity prompts

A `ProximityPrompt` is an instance you put inside a part to get "Press E to open" with no interface
to write. The engine draws the prompt, handles the key and the hold, and tells you when it was
triggered.

```lua
local prompt = door:add("ProximityPrompt", {
    actionText = "Open", objectText = "Door",
    keys = "e", holdDuration = 0.5,
    maxActivationDistance = 8, requiresLineOfSight = true,
    offset = vec3(0, 1, 0),
    backgroundColor = color(0.05, 0.05, 0.07), backgroundTransparency = 0.25,
    textColor = color(1, 1, 1), keyColor = color(1, 1, 1),
})

-- server: fires after checking the player really is close
prompt.triggered:connect(function(body) door:destroy() end)
prompt.holdBegan:connect(function(body) end)
prompt.holdEnded:connect(function(body) end)

-- client
prompt.shown:connect(function() end)
prompt.hidden:connect(function() end)
```

- `actionText` is what the player is offered to do and `objectText` names what they are doing it to.
  The colour properties are what the box behind them looks like.
- `holdDuration = 0.5` means the player holds the key for half a second. The engine draws the
  progress bar while they hold, and `triggered` fires at the end of it.
- `maxActivationDistance = 8` is how far away the prompt still works, and
  `requiresLineOfSight = true` hides it when something is in the way.
- `offset` moves the prompt off the centre of the part it is parented to, a metre up here.
- `triggered`, `holdBegan` and `holdEnded` all fire with the body that did it, on the server, after
  the engine has checked that the player really is close. `shown` and `hidden` fire on the client,
  which is where you play a sound or change a cursor.

A client shows the one prompt nearest to its player, floating over what it is parented to, with a
progress bar while held. `keys` uses the same key names as `InputAction`.

You can ask which prompt a body would use right now:

```lua
local prompt, distance = game.proximity:closestInteractable(body)
```

## Click detectors

A `ClickDetector` inside a part hears the player click it:

```lua
-- server: a lever that opens and closes a door
local lever = world:find("Lever") :: Part
local door = world:find("Door") :: Part
local detector = lever:add("ClickDetector", { maxActivationDistance = 6 })
local open = false

detector.mouseClick:connect(function(player)
    open = not open
    door.collides = not open
    door.transparency = if open then 0.8 else 0
    lever.cframe = lever.cframe * cframe.angles(0, 0, if open then 0.8 else -0.8)
    print(player.name, if open then "opened" else "closed", "the door")
end)
```

- The detector goes inside the part you want clicked, so `lever:add("ClickDetector", ...)` is what
  makes the lever clickable.
- `mouseClick` fires with the player who clicked, which is what `player.name` prints here.
- Because this runs on the server, the door opens for everybody: `collides`, `transparency` and
  `cframe` are properties, and writing them replicates to every client.

| Property | |
|---|---|
| `maxActivationDistance` | how far the player's body may be, 32 by default |
| `cursorIcon` | the pointer while over it: empty for a hand, a cursor name like `"crosshair"`, or `"res://ui/lever.png"` |

| Event | Fires with | |
|---|---|---|
| `mouseClick` | the player | a left click |
| `rightMouseClick` | the player | a right click |
| `mouseHoverEnter` | the player | the pointer comes onto it |
| `mouseHoverLeave` | the player | the pointer leaves it |

What decides whether a click counts:

- The player aims with the crosshair, or with the mouse while the pointer is free. `cursorIcon`
  only shows while the pointer is free.
- A detector inside a `Model` hears every part of the model. When detectors are nested, the one
  closest to the part that was aimed at wins.
- Blocks in between stop it, and nothing inside a `ViewportFrame` counts.
- A click fires when the button is released over the same detector it was pressed on. The click
  still reaches Minecraft; bind an action to `mousebutton1` to keep it from the game.
- Nothing is aimed at while a screen, the chat or the editor is open, so opening one fires
  `mouseHoverLeave`. A click an interface took does not count.
- Distance is counted from the player's body to the part, less half its size.

On the server each event fires after checking the player really is within `maxActivationDistance`,
with two blocks to spare for lag. In a `LocalScript` they fire at once with the local player, where
`name`, `id`, `character` and `controls` work, and `spawn` and `kick` are errors.

Hover events are the ones you usually want on the client, because a hint only that player should see
belongs on their own screen:

```lua
-- client: a hint while the pointer is on the lever
local detector = world:find("Lever").ClickDetector :: ClickDetector
detector.mouseHoverEnter:connect(function() hint.visible = true end)
detector.mouseHoverLeave:connect(function() hint.visible = false end)
```

## Watching two things

A watcher tells you when two instances come within a distance of each other, without you measuring
it yourself every tick:

```lua
local watcher = game.proximity:watch(player, chest, 6)
watcher.entered:connect(function(a, b) end)
watcher.left:connect(function(a, b) end)
watcher.near       -- true while they are within range
watcher:stop()
```

`entered` fires when they come within the six metres and `left` when they go back out, both with the
two instances. Read `near` when you want the answer now rather than a signal. Call `stop()` when you
are done with the watcher.

## Falloff

`falloff` turns a distance into a number between 1 and 0, which is what you multiply a volume or a
strength by so it fades with range:

```lua
local volume = game.proximity.falloff(distance, 4, 30, 1.5)   -- 1 inside 4 m, 0 past 30 m
```

The first three arguments are the distance you measured, the range that still counts as full
strength, and the range past which there is nothing left. The fourth shapes the curve between them.
