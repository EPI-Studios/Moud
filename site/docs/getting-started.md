# Getting started

This page walks through a whole place, from the project hub to a floor you can stand on and a camera
that switches to third person when you sneak. It takes two files and no setup beyond creating the
project.

## Create a place

**New project** in the project hub offers three starts:

- **Baseplate** gives you a 128 m floor to build on. Use it when you want to try something out
  straight away.
- **Main menu** gives you a menu drawn over a camera flying along a `CameraPath`. **Play** hands the
  player a body, <kbd>Escape</kbd> opens a pause menu, and `place.toml` sets a loading screen. Players
  wait in the menu because the template sets `game.players.autoSpawn = false`. The menus are built in
  `client/main.luau`, and the scene holds the floor, the `SpawnLocation`, the camera path and the
  `Menu` remote.
- **Empty** gives you a place with nothing in the scene.

Each template is a folder of ordinary files. Nothing in it is generated at build time, so you can
read and change every part of it.

## Write the server script

`server/main.luau` runs on the server. It builds the world and decides what happens to the players in
it:

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

Line by line:

- `world:add("Part", { ... })` creates a part and parents it to the world. `size` is in metres,
  `cframe` places its centre at x 0, y 64, z 0, and `anchored = true` keeps physics from moving it,
  which is what you want for a floor.
- `game.players.joined` fires once for each player who connects. It exists on the server only,
  because the server is the side that knows about everyone.
- `player:spawn(position)` gives that player a body and puts it where you asked. Two metres above the
  floor is enough to land on it.
- `player.character` is the body. Its `humanoid` decides how it moves, so setting `walkSpeed = 6`
  makes that player walk six metres a second.

<!-- demo:getting-started-spawn -->

## Write the client script

`client/main.luau` runs inside each player's own game. It sets the camera, reads input and draws
interface for that player alone:

```lua
camera.fov = 80

game.renderStepped:connect(function()
    camera.mode = input:down("sneak") and "thirdPerson" or "firstPerson"
    camera.distance = 6
end)
```

`camera` and `input` are globals that exist on the client only. `renderStepped` runs once a frame,
just before the frame is drawn, so the camera you set inside it is the camera the world is drawn
against.

<!-- demo:getting-started-camera -->

That is a whole place. There is no registration step and nothing to declare: a file that exists is a
file that runs.

## Change something while it runs

Save any file in the place and Moud restarts it. Everything the place created is destroyed and made
again, which means you can change a number, save, and see the result without leaving the game.

It also means a variable that holds an instance from before the reload points at something that no
longer exists:

```lua
-- wrong: points at an instance the reload destroyed
local statue = game.world:find("statue")

-- right: looked up when it is needed
local function statue()
    return game.world:find("statue")
end
```

> [!WARNING]
> Every variable in your scripts is cleared by a reload. Anything that has to survive one goes in
> `game.persist`, a plain table the engine hands back after each restart:
>
> ```lua
> game.persist.joins = (game.persist.joins or 0) + 1
> ```

<!-- demo:getting-started-reload -->

## The two clocks

Each side has its own loop:

```lua
-- the server, once a tick. delta is seconds
game.stepped:connect(function(delta) end)

-- the client, once a frame. delta is seconds
game.renderStepped:connect(function(delta) end)
```

Write game state on `stepped`, where every player gets the same answer. Write the camera and anything
that has to look smooth on `renderStepped`, which runs often enough to follow the frame rate.

<!-- demo:clocks -->

## Waiting without blocking

`task` lets a script wait without stopping the tick that everyone else is on:

```lua
task.spawn(function()
    while true do
        task.wait(0.6)
        lantern.color = color(1, 0.88, 0.5)
    end
end)

task.delay(3, function() end)

local handle = task.spawn(function() end)
task.cancel(handle)
```

`task.spawn` starts a piece of work that can yield, `task.wait` yields for a number of seconds,
`task.delay` runs something once later, and `task.cancel` stops work you started.

> [!TIP]
> A loop without a `task.wait` inside it never gives the tick back, and the server stops answering
> anybody. If you write `while true do`, put a wait in it.

## When something goes wrong

A value an enum does not accept is an error that names what was allowed, instead of quietly falling
back to a default:

```lua
camera.mode = "birdseye"
-- error: mode wants one of firstPerson, thirdPerson, scriptable
```

Errors are listed in the in-game overlay, which opens with <kbd>Right Shift</kbd>. The overlay also
shows what the renderer and the physics currently think, so it tells you whether the place is wrong
or the engine is.

## Next steps

- [Places, modules, scenes and scripts](place.md) explains `place.toml`, `require`, scene files and
  the script instances you can put in the tree.
- [Instances](instances.md) covers parts, properties, signals and raycasts in full.
- [Players and bodies](players.md) covers spawning, teams and walking bodies around.
- [Interface](interface.md) covers the screen, billboards and surface guis.
