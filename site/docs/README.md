# Writing a place in Luau

Moud is a game engine that runs inside Minecraft. You write your game in Luau, Moud runs it while
Minecraft is running, and you ship the result as a mod. Everything your code creates lives in a tree
of instances that the engine draws, collides and replicates for you.

The game you write is called a **place**. A place is a folder of files: Moud reads the folder when
the game starts, runs the entry points it finds inside it, and runs them again every time you save.

Vanilla Minecraft features start switched off. Hunger, fall damage, block breaking, the hotbar and
the sky each have a setting in `place.toml`, and you turn on only the ones your game needs.

## A place is a folder

A new place has this layout:

```
place/
  place.toml           the name, the entry points, and which vanilla features are on
  server/main.luau     the world, and everyone in it
  client/main.luau     the camera, the input, and this client's own body
  shared/              modules both sides require
  scenes/              .scene files laid out in the editor
  .moud/types.d.luau   generated when the place starts; never edit it
  .luaurc              the @server, @client and @shared require aliases, written once
  .zed/settings.json   points Zed's Luau server at the types, kept in sync
```

Two files do the work:

- **`server/main.luau`** runs on the server. It builds the world, decides what happens in it, and is
  the only side allowed to change what every player sees.
- **`client/main.luau`** runs inside each player's own game, once per player. It sets that player's
  camera, reads their input and draws their interface.

The rest of the folder supports those two:

- **`place.toml`** holds the name of the place, the entry points and a switch for each vanilla
  Minecraft feature. See [Places, modules, scenes and scripts](place.md).
- **`shared/`** holds modules that both sides load with `require`, such as a table of weapon stats
  the server uses to deal damage and the client uses to fill a menu.
- **`scenes/`** holds `.scene` files. A scene is a piece of the world you lay out in the editor
  instead of writing by hand, and a script loads it when it is needed.
- **`.moud/types.d.luau`** is written by the engine every time the place starts. It gives your editor
  the type of every class and property, which is what makes autocomplete and strict mode work.

> [!NOTE]
> There is no registration step and nothing to declare. A file that exists is a file that runs, and
> adding a script never means editing a list somewhere else.

## Saving reloads the place

When you save a file, Moud destroys everything the place created and runs it again from the top. You
do not restart Minecraft to test a change, and what you see in the game is always what the code
currently says.

Because everything is created again, a variable holding an instance from before the reload points at
something that no longer exists. Look instances up at the moment you use them:

```lua
-- wrong: this points at an instance the reload destroyed
local statue = game.world:find("statue")

-- right: looked up when it is needed
local function statue()
    return game.world:find("statue")
end
```

> [!WARNING]
> A reload also clears every variable in your scripts. Values that have to survive one, such as a
> score or a round number, go in `game.persist`, a plain table the engine hands back after each
> restart.
>
> ```lua
> game.persist.joins = (game.persist.joins or 0) + 1
> ```

## The server owns the tree, each client mirrors it

The server holds the real tree of instances. Every player's game holds a copy of it. When a server
script creates a part, moves it or changes one of its properties, the engine sends that change to
every client and applies it there. Nothing travels back the other way on its own.

That rule decides where each API lives:

| | `server/main.luau` | `client/main.luau` |
|---|---|---|
| `game.world` | the real tree | a mirror of it |
| `game.stepped` | yes, once a tick | no |
| `game.renderStepped` | no | yes, once a frame |
| `game.players.joined` / `.leaving` | yes | no |
| `game.players:me()` | no | yes |
| `camera`, `input` | no | yes |
| `task`, `game.persist`, `game.reloaded` | yes | yes |

<!-- demo:replication -->

What a client script adds to the tree stays on that client. It is drawn, other client scripts can
find it, and no other player ever sees it. That is what you want for interface, previews and effects
only the local player needs.

> [!IMPORTANT]
> A client cannot change the world for anybody else, and writing to its own mirror does not ask the
> server for anything. When a player needs something to happen for everyone, the client sends a
> message and the server decides what to do with it. See
> [Talking across the boundary](talking.md).

## Everything you create is an instance

Parts, characters, lights, buttons, sounds and scripts are all instances. You create one with `add`,
which takes a class name and a table of properties, and returns the new instance:

```lua
local floor = game.world:add("Part", {
    name = "floor",
    size = vec3(40, 1, 40),
    cframe = cframe(0, 64, 0),
    color = color(0.35, 0.42, 0.38),
    anchored = true,
})
```

The properties in that table do the same thing as assigning them afterwards, so the part above is the
same part as one you create empty and then fill in. Writing a property later changes the instance
immediately, for every player who can see it:

```lua
floor.color = color(0.8, 0.2, 0.2)
floor.transparency = 0.5
```

The tree is how you reach an instance again. `add` parents the new instance to whatever you called it
on, `find` looks a child up by name, and `destroy` removes an instance along with everything under
it:

```lua
local lamp = floor:add("PointLight", { brightness = 4, range = 12 })

game.world:find("floor")     -- the part above
floor:find("PointLight")     -- the light inside it

lamp:destroy()
```

Instances also have signals, which are events you connect a function to. The function runs every time
the event happens, until you disconnect it or the instance is destroyed:

```lua
floor.touched:connect(function(other)
    print(other.name .. " stepped on the floor")
end)
```

Every class follows that pattern. Once you know `add`, `find`, `destroy` and `connect`, a class you
have not used yet is only a list of properties and signals to read.

> [!TIP]
> Every class, property and signal is listed in the [Reference](reference.md), and your editor knows
> them too, because the engine writes them into `.moud/types.d.luau` each time the place starts. A
> misspelled property shows up in the editor before you run anything.

## A player and their body are two different things

The **player** is the person connected to the server. The **character** is the body they wear in the
world. A player can be connected without a body: before they spawn, while they are dead, or the whole
time they sit in a menu.

```lua
game.players.joined:connect(function(player)
    player:spawn(vec3(0, 66, 0))
    player.character.humanoid.walkSpeed = 6
end)
```

A character is an instance like any other, made of parts joined together:

- **`humanoid`** carries health and decides how the body moves, through properties such as
  `walkSpeed` and `jumpHeight` and calls such as `takeDamage`.
- **`animator`** plays `.anim` files on the body and blends them by priority.
- **`backpack`** holds the tools the player carries, which is what fills their hotbar.

The server spawns bodies and says what happens to them. The client that owns a body is the one that
moves it, which is why walking feels immediate, and why the server checks anything that matters. See
[Characters](character.md), [Players and bodies](players.md) and [Tools](tools.md).

## Interface, sound and effects are instances too

There is no separate UI system to learn. A `ScreenGui` draws over the screen, a `BillboardGui` floats
over an instance in the world, and a `SurfaceGui` sticks to one face of a part. All three hold the
same widgets, and the engine reads them every frame, so a property you write shows up on the next
one:

```lua
-- client
local hud = game.world:add("ScreenGui", { name = "hud" })

hud:add("TextLabel", {
    position = udim2(0, 16, 0, 16),
    size = udim2(0, 200, 0, 24),
    text = "Score: 0",
    textColor = color(1, 1, 1),
    backgroundTransparency = 1,
})
```

Sounds, particle emitters, lights and post effects work the same way: you add them to the tree, set
their properties, and destroy them when you are done with them. Even the time of day is an instance:
a `Lighting` in the world takes the clock, the sky and the weather over. See
[Interface](interface.md), [Sound](sound.md),
[Models, lights, post effects and held items](rendering.md) and
[Lighting, sky, weather and presets](lighting.md).

## The two clocks

The server and the client each have their own loop, and different work belongs in each one:

```lua
-- server, once a tick. delta is how many seconds passed
game.stepped:connect(function(delta) end)

-- client, once a frame. delta is how many seconds passed
game.renderStepped:connect(function(delta) end)
```

Game state belongs on `stepped`, where every player gets the same answer. The camera and anything
that has to look smooth belong on `renderStepped`, which runs just before the frame is drawn, so the
camera you set there is the camera the world is drawn against. See
[Tweens and timing](tweens-and-timing.md).

## Units

Metres and seconds, everywhere. Angles are radians, and colours run from 0 to 1.

Nothing in a place is stated per tick. `walkSpeed = 6` means six metres a second whatever the tick
rate is, and the engine does the dividing.

<!-- demo:color -->

> [!TIP]
> Press <kbd>Right Shift</kbd> in game to open the overlay. It lists the errors your scripts have
> raised, along with what the renderer and the physics currently think, which is the quickest way to
> tell whether your place is wrong or the engine is. See [Debugging](debug.md).

## Browse the docs

- [Getting started](getting-started.md): the smallest place that does something
- [Places, modules, scenes and scripts](place.md): place.toml, require, scene files, Script and ModuleScript instances
- [Containers](containers.md): server storage, replicated storage and the starter folders: what is sent, what runs, what is in the world
- [Instances](instances.md): the tree, parts, signals, attributes, raycasts
- [Working with the tree](tree.md): finding, selector queries, hierarchy signals, waiting, values, copies, callbacks
- [Characters](character.md): the body, its joints, its skin and its animation
- [Animations](animation.md): .anim files, keyframe sequences, tracks, fades and markers
- [Players and bodies](players.md): finding players, asking a body, teams, leaderstats, paths and following
- [Tools](tools.md): the backpack, the hotbar, clicking, handles, starter packs, dropping
- [Movement, collision and input](movement.md): humanoids, seats, collision groups, input actions, ownership
- [Building in the editor](editor.md): joining parts with the constraint tool, welding the selection, seeing constraints
- [Editor plugins](plugins.md): toolbar buttons, commands and shortcuts, panels, selection, undoable scene edits, settings, viewport clicks
- [Physics](physics.md): falling parts, shapes, pushing, who simulates a part, models, welds, hinges, ropes, winches, springs and rods
- [Camera and input](camera-and-input.md): the client's own two globals
- [Pausing, quitting, settings and screens](game-and-screens.md): Escape, game.paused, settings, the loading screen, exported games
- [Windows](window.md): the game window, more windows, overlays
- [Queries](queries.md): casts, overlaps, options, helpers, lag compensation
- [Blocks](blocks.md): reading, shapes, copy and paste, change events
- [Zones, touches, tags, prompts and clicks](zones-and-triggers.md): regions, triggers, "press E", clickable parts
- [Interface](interface.md): screen, billboard and surface guis, layouts, text boxes, scrolling, mouse events
- [Chat](chat.md): channels, delivery, messages, the window, bubbles, commands
- [Rich text](rich-text.md): every tag, links, hover, pictures, text shaders
- [Images](images.md): fitting, nine-slice, sprite sheets, drawing images in code, loading files, textures and skins
- [Sound](sound.md): sound instances, buses, sound effects and the audio global
- [Models, lights, post effects and held items](rendering.md): models, lights, post effects and what a body holds
- [Lighting, sky, weather and presets](lighting.md): the day clock, the sun, skyboxes, distance fog, clouds, rain, snow, storms and lightning, and presets that set a whole look at once
- [Particles, beams, trails, highlights and decals](effects.md): emitters, fire, smoke, bursts, selection boxes, explosions
- [Mixins, Java and shader patches](mixins.md): hooks, mixin files, state, conversions, shaders
- [Other languages](languages.md): places in Revo or Java, and writing a language addon
- [Tweens and timing](tweens-and-timing.md): tweens, task, signal helpers, which side runs, the render step, debris, cooldowns
- [Math](math.md): vectors, frames, rotations, numbers, random
- [Saving](saving.md): stores and sessions
- [Memory stores](memory.md): expiring hash maps, sorted maps and queues shared by server scripts
- [Reaching websites](http.md): http requests from the server, headers, json, limits
- [Talking across the boundary](talking.md): channels, remote functions, what may cross, messaging and shared state
- [Debugging](debug.md): drawing, watching, query stats, profiling
- [Reference](reference.md): every class, every property
