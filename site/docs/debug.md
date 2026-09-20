# Debugging

When a place does not do what you meant, you need to see what it actually did. Moud gives you five
ways to look: printing to the log, drawing shapes in the world, watching values on the screen,
measuring what queries and scripts cost, and the in-game overlay. Most of them hang off
`game.debug`, which exists on both sides.

## print

`print` writes to the game log (`logs/latest.log`), marked `[server]` or `[client]`.

The mark tells you which side ran the line, which is usually the first thing you want to know when a
script exists on both sides.

## Drawing

Drawing puts a shape or a label over the world so you can see a position, a direction or a box that
your code is working with. Each call takes a number of seconds to stay up, and draws for one frame
when you leave the time out. Called on the server, they show on every client.

```lua
local d = game.debug
d:drawLine(from, to, color(1, 0, 0), 2)
d:drawRay(from, direction, color(0, 1, 0), 2)
d:drawBox(part.worldCframe, part.size, nil, 2)
d:drawSphere(position, 3)
d:drawPoint(position)
d:label(position, "spawn", color(1, 1, 1), 5)
d:clear()
```

- `drawLine` goes between two positions; `drawRay` goes from a position along a direction, which is
  the one that matches what you handed `world:raycast`.
- `drawBox` takes a frame and a size, so passing `part.worldCframe` and `part.size` draws exactly
  where a part is, rotation included. Passing `nil` for the colour uses the default.
- `label` writes text in the world at a position, for naming the thing you just drew.
- `clear` removes everything currently drawn, without waiting for the times to run out.

Drawing from a `renderStepped` or `stepped` handler with no time given gives you a shape that follows
the thing every frame, because it is drawn again each time.

<!-- demo:debug-draw -->

## Watching values

`watch` puts a name and a value in a live list in the top right of the screen. Call it every tick
with the same name and the row updates in place rather than scrolling past you, which is what `print`
would do:

```lua
game.stepped:connect(function()
    game.debug:watch("players", game.players:count())
end)
```

Values watched on the server are marked `(server)`, so a name watched from both sides shows twice and
you can see them disagree.

<!-- demo:debug-watch -->

## What queries cost

```lua
local stats = game.debug:queryStats()   -- { queries, partsTested } since the last call
```

`queries` is how many casts and overlaps ran, and `partsTested` is how many parts they looked at
between them. Both count from the last time you called it, so calling it on a timer gives you a rate.
A `partsTested` that climbs while `queries` stays flat means each query is reaching further than you
meant. See [Queries](queries.md).

## Profile

`profile` tells you how long each script's handlers and each callback took since the last call,
slowest first:

```lua
task.every(5, function()
    for where, t in game.debug:profile() do
        print(where, t.milliseconds, t.calls, t.worst)
    end
end)
```

Each row gives you `milliseconds` spent in total, `calls` as how many times it ran, and `worst` as
the slowest single call. A high total with many calls is different work from a high total caused by
one slow call, and `worst` is what tells them apart.

<!-- demo:debug-profile -->

`main` is the place's own script; a `Script` instance shows under its name; a callback shows as
`Class.name`, like `TextChannel.shouldDeliver`.

## The overlay

<kbd>Right Shift</kbd> opens the in-game overlay with script errors and what the renderer and physics
currently think. Its **mixins** tab lists every hook a place put on the game, with calls and time per
second, errors, and a switch to turn one off (see [mixins](mixins.md)).

> [!TIP]
> The overlay is the quickest way to tell whether your place is wrong or the engine is. If your
> script raised, the error is listed there with the rest; if it did not, what the renderer and the
> physics report tells you where the value actually went.

## Seeing collisions

<kbd>F7</kbd> (rebindable in Controls, under Moud) draws what the physics collides against, over the
world. Each colour is a different kind of collider, so the colour tells you which path the physics
took for that part:

| Colour | What it is |
|---|---|
| green | a part's box exactly as the physics received it this tick |
| green triangles | the faces a [shaped part](physics.md#shapes) collides as |
| cyan | a turned part colliding as its real turned box |
| orange | a turned part colliding as the upright box around it, because turned collision is unavailable |
| grey | a part with `collides = false` |
| white | the collision shapes of blocks within 5 blocks of you |
| yellow | every body's capsule: players, mobs and the characters a place walks |

Orange is the one to look for: a part that should collide at an angle and shows orange is being
collided against as an upright box, which is why something slides off it or catches on nothing.

<!-- demo:debug-colliders -->

The top right says how many boxes, shape faces, turned parts, ghosts, block boxes and bodies are
within 40 blocks. It is drawn over everything, walls included, so you can see a collider through the
floor it sits under.
