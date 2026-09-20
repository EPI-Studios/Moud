# Tweens and timing

This page covers the ways a place says "later". A tween moves properties toward a value over time,
`task` waits without stopping the tick everyone else is on, signals let you wait for something to
happen, and the render step puts work at a chosen point inside a frame. The clock and date functions
at the end tell you what time it is, and `bindToClose` gives you one last moment as the place shuts
down.

## Tweens

A tween moves one or more properties of an instance from what they are now toward the goals you give
it, over a number of seconds:

```lua
local t = door:tween({ cframe = cframe(0, 70, 0), transparency = 0.5 }, {
    time = 1,              -- seconds
    easing = "quad",       -- linear, sine, quad, cubic, quart, quint, expo, circ, back, elastic, bounce
    direction = "out",     -- in, out, inOut
    repeats = 0,           -- how many extra times
    reverses = false,      -- go back to the start after each run
    delay = 0,
})
t:pause()  t:resume()  t:cancel()
t:state()              -- playing, paused, completed, cancelled
t.completed:connect(function(finished) end)   -- true when it ran out, false when cancelled
```

- The first table is the goals. Every key is a property of the instance, and the tween starts from
  whatever that property holds when the tween starts.
- `easing` and `direction` decide the shape of the movement. `easing = "linear"` covers the same
  distance every frame; the others speed up, slow down or overshoot, and `direction` says which end
  of the run that happens at.
- `repeats` is how many extra runs happen after the first, and `reverses` makes each run go back to
  where it started before the next one.
- `delay` holds the tween still for that many seconds before it starts moving.
- `completed` fires with `true` when the tween ran to the end and `false` when something cancelled
  it, so one handler can tell the two apart.

<!-- demo:tweens-and-timing-repeats -->

Numbers, vectors, rotations, frames and colours ease smoothly. A tween started on the server moves on
every client as smoothly as one started there. A tween belongs to the script that started it and
stops when that script does.

A goal the class does not have is an error, and so is a read-only property: `part:tween({ velocity
= ... })` raises `Part.velocity is read-only`, the same as writing it would.

<!-- demo:easing -->

## task

`task` schedules work. Each call either waits, starts something that can wait, or hands you a handle
you can cancel:

```lua
task.wait(0.5)                       -- yield, returns how long it waited
local handle = task.spawn(function() end)
task.delay(2, function() end)
task.defer(function() end)           -- after the step that is running now
task.cancel(handle)

local timer = task.every(1, function() end)      -- every second until cancelled
timer:cancel()
local later = task.after(3, function() end)      -- once, unless cancelled first
later:cancel()

local save = task.debounce(function() end, 0.5)  -- runs once things are quiet for 0.5 s
local shoot = task.throttle(function() end, 0.2) -- at most once every 0.2 s
```

- `task.wait` yields and returns how long it waited, which is what you read when you need the real
  elapsed time rather than the one you asked for.
- `task.spawn`, `task.every` and `task.after` hand back a handle. Keep it if you want to stop the
  work later.
- `save` and `shoot` are functions you call. `debounce` runs the one you wrapped once calls stop for
  the number of seconds you gave, and `throttle` runs it at most that often however fast you call it.

<!-- demo:tweens-and-timing-debounce -->

`task.defer` runs a function once the step that is running now is finished, without waiting a set
time. Use it to let a whole batch of changes land before you look at the result:

```lua
for _, part in wall:descendants() do part:destroy() end
task.defer(function() print(#wall:descendants(), "left") end)
```

> [!IMPORTANT]
> Anything that waits (`task.wait`, `signal:wait`, `waitForChild`) has to run inside a function
> started with `task.spawn`. A signal handler that needs to wait starts one:
>
> ```lua
> prompt.triggered:connect(function(body)
>     task.spawn(function()
>         task.wait(1)
>         door:destroy()
>     end)
> end)
> ```

## Signals

A signal is something that happens, and `connect` runs your function every time it does. Every
signal, whether the engine made it or you did, has these on top of `connect`:

```lua
game.stepped:once(function(dt) end)             -- the next firing only
game.stepped:every(20, function(dt) end)        -- every 20th firing
local dt = game.stepped:wait()                  -- yield until it fires
local who = prompt.triggered:wait(10)           -- nil after 10 seconds
```

`wait` hands back whatever the signal fired with. Given a number it gives up after that many seconds
and returns `nil`, which is how you tell "nobody pressed it" from "somebody did".

A place makes its own signals too, for one part of your code to tell another that something
happened:

```lua
local hit = signal("hit")
hit:connect(function(amount) print("hit for", amount) end)
hit:once(function(amount) print("first hit") end)
hit:fire(12)                                    -- every listener, in the order they connected
print(hit.count)                                -- how many are connected
hit:disconnectAll()
```

- `signal(name)` makes one. The name is what it is called, and you hold on to the value it returns.
- `fire` passes its arguments straight to every listener, in the order they connected.
- `count` is how many listeners are connected right now, and `disconnectAll` drops all of them.

A signal made by the engine (`game.stepped`, `part.touched`...) can not be fired by a script.

## Which side is running

```lua
if game.isServer then end
if game.isClient then end
if game.isStudio then end
```

`isServer` and `isClient` say where this script runs, which matters in a module under `shared/` that
both sides require. `isStudio` is true while you playtest a place opened in the editor, on both
sides. It is false in a place launched straight into play and in an exported game.

## The render step

`game.renderStepped` runs once a frame before the camera is placed. `bindToRenderStep` picks where
in the frame a function runs, by priority, and gives it a name to unbind it by:

```lua
-- client: a lamp that hangs in front of the camera wherever it ends up this frame
local lamp = game.world:add("Part", { name = "lamp", size = vec3(0.2, 0.2, 0.2), anchored = true })

game:bindToRenderStep("lamp", game.renderPriority.camera + 1, function(dt)
    lamp.cframe = camera.cframe * cframe(0.4, -0.3, -1)
end)

game:unbindFromRenderStep("lamp")                 -- later, to stop it
```

- `"lamp"` is the name you unbind by, and `game.renderPriority.camera + 1` puts the function just
  after the camera is placed, so `camera.cframe` is this frame's.
- The frame the lamp gets is the camera's frame times an offset, which is what keeps it in front of
  the camera wherever the player looks.

| `game.renderPriority` | |
|---|---|
| `first` | 0 |
| `input` | 100 |
| `camera` | 200 |
| `character` | 300 |
| `last` | 2000 |

- Lower runs first. **200 and below runs before the camera is placed**, so write the camera there.
  Above 200 runs after, with this frame's `camera.cframe`.
- The function is handed the frame's `dt` in seconds.
- `game.renderStepped` fires after the bindings of 200 and below, still before the camera.
- Binding a name that is bound replaces it.
- A binding is dropped when the script that made it stops.
- `bindToRenderStep` and `unbindFromRenderStep` are client-only; the server has no frames.

<!-- demo:tweens-and-timing-renderstep -->

## Debris

`game.debris` destroys an instance for you after a number of seconds, which saves you writing a wait
around every shell casing and puff of smoke:

```lua
game.debris:addItem(shell, 2)      -- destroyed in 2 seconds
game.debris:addItem(smoke)         -- in 10
```

`addItem` doesn't wait and doesn't need `task.spawn`. The countdown keeps going if the script that
started it stops, and an instance destroyed before its time is simply forgotten. The world itself
can't be added.

## Errors

`pcall` and `xpcall` catch errors from the engine as well as from `error`, and hand back the message as
text:

```lua
local ok, err = pcall(function() game.world:add("NotAClass") end)
print(ok, err)   -- false   there is no class 'NotAClass'...
```

## Cooldowns

`cooldown` keeps one timer per body per name, so a player can dash again only once their own dash
timer has run out:

```lua
if cooldown:ready(body, "dash", 2) then
    -- dash, and start a 2 second cooldown for this body
end
cooldown:remaining(body, "dash")
cooldown:reset(body, "dash")
```

- `ready(body, name, seconds)` answers true when that body's timer for that name has run out, and
  starts a new one of `seconds` at the same time. It answers false while the timer is running, and
  starts nothing.
- `remaining(body, name)` is how much of the timer is left, in seconds.
- `reset(body, name)` clears the timer, so the next `ready` answers true.

## Clock

`clock()` is seconds on a steady clock, for measuring how long something took. `os.clock()` is the
same clock under Luau's name.

## Date and time of day

`os` reads the wall clock, for stamping a save or showing the time in a gui:

```lua
os.time()                          -- seconds since 1970
os.date()                          -- "2026-09-17 20:41:03"
os.date("%H:%M")                   -- "20:41"
os.date("!%Y-%m-%d", 0)            -- "1970-01-01", in UTC
os.difftime(later, earlier)        -- later - earlier, in seconds
```

`os.date(format, when)` formats `when`, or now when you leave it out. A `!` in front of the format
reads it in UTC; without one it is the machine's own time zone.

| | |
|---|---|
| `%Y` `%y` | year, four digits or two |
| `%m` `%d` | month and day, padded |
| `%H` `%M` `%S` | hour on 24, minute, second |
| `%I` `%p` | hour on 12, and AM or PM |
| `%A` `%a` | Thursday, Thu |
| `%B` `%b` | September, Sep |
| `%j` | day of the year |
| `%%` | a per cent sign |

Anything else after a `%` is left as it is. Names are English whatever the player's language is.

<!-- demo:tweens-and-timing-date -->

`os.time` moves with the machine's clock, so it is for showing and stamping. Use `clock()` to
measure how long something took.

## When the place stops

`game:bindToClose(handler)` runs a function as the place shuts down: a hot reload, leaving a
playtest, or the server stopping. It works on the server and on a client.

```lua
-- server
game:bindToClose(function()
    for _, player in game.players:list() do
        rounds:set(player.id, wins[player.id] or 0)
    end
end)
```

Bind more than one and they all run.

> [!WARNING]
> The handler runs on the way out, so it cannot wait. Do the last write and return.
