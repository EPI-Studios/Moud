# Tweens and timing

This page covers the ways a place says "later". A tween moves properties toward a value over time,
a timeline plays keyed properties, sounds and callbacks on one clock, `task` waits without stopping the tick everyone else is on, signals let you wait for something to
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

## Timelines

A timeline is one clock that moves any number of properties through keys you set, fires callbacks
at chosen times, and starts sounds and videos. A splash screen is one of them instead of a pile of
`task.delay` calls:

```lua
-- client
local splash = hud:add("Timeline", { name = "splash" })
splash:addKey("video", "playing", 0, true)
splash:addKey(music, "playing", 7, true)
splash:setKeys("logo", "imageTransparency", { { 13, 1, "sine-out" }, { 15, 0 } })
splash:setKeys("black", "backgroundTransparency", { { 17.5, 1 }, { 19.6, 0 } })
splash:at(20, function() hud:destroy() end)
splash:play()
```

- `addKey(target, property, time, value, shape)` adds one key, and `setKeys(target, property, keys)`
  replaces every key of that property with a list of `{ time, value, shape }`. Both return the
  `PropertyTrack` that holds the keys, which is created the first time a property is keyed.
- The target is an instance, or a path from the timeline's parent: `"logo"` is a sibling of the
  timeline, `"panel/title"` a child of a sibling and `"../door"` a sibling of the parent.
- Between two keys the property moves the way the first key's shape says: `"linear"` (the default),
  `"cubic"` for a smooth curve through the keys around it, `"constant"` to hold until the next key,
  or an easing and direction like `"quad-out"` or `"sine"` (which is `"sine-inOut"`).
- Before its first key a property holds the first key's value, and after its last key the last one.
- Numbers, vectors, colours, udims, rotations and frames move smoothly; a frame turns along the
  shortest way. A boolean like `playing` only changes when the clock passes one of its keys, so a
  sound that finished is not started again, and a script can still set it between keys.
- `at(time, callback)` runs a function when the clock passes that time. It belongs to the script that
  added it and is not saved with the scene.
- The length grows to fit the keys and callbacks you add. Set `length` to hold at the end for longer.

```lua
splash:play()   splash:pause()   splash:stop()     -- stop goes back to 0
splash.timePosition = 12                            -- jump, even while paused
splash.speed = -1                                   -- backwards
splash.looped = true
splash:addMarker(15, "logo")                        -- a named moment, saved with the scene
splash.reached:connect(function(name) end)          -- "logo" at 15 s
splash.ended:connect(function() end)
splash.didLoop:connect(function() end)
```

- **`length`** is where the clock ends, in seconds. At the end a timeline stops, or starts again
  from 0 when **`looped`** is true, firing `didLoop`.
- **`speed`** scales the clock. **`playing`** is true while it runs; setting it in the scene starts
  the timeline when the place starts.
- **`timePosition`** is where the clock is. Setting it shows that moment straight away.
- **`markers`** are `reached`'s names and times, written `"15 logo, 20 done"`.

A cubic curve on a looped timeline runs from the last key round to the first, so a loop has no
corner at the seam. Turn that off per track with `loopWrap = false`.

### Tracks in the scene

Each keyed property is a `PropertyTrack` inside the timeline, so a timeline built in a script or in
the editor is saved with the scene like anything else. Its keys are text, one `time value shape`
entry per key:

```lua
local track = splash:addKey("logo", "position", 2, udim2(0.5, 0, 0.3, 0), "cubic")
print(track.keys)          -- 2 0.5 0 0.3 0 cubic
print(track:getKeys())     -- { { 2, UDim2, "cubic" } }
```

- **`target`** is the instance the track moves, and **`path`** is used when there is none.
- **`property`** is the property it moves.
- **`keys`** are the keys. A frame is written as a position and a quaternion, a colour as `r g b`
  from 0 to 1.
- **`interpolation`**, **`easing`** and **`direction`** are what keys without a shape of their own
  use.

### Where a timeline runs

A timeline runs on the side that made it. One the server made writes its properties there, and they
reach every client the way any change does: a part moves smoothly between updates. Every client
also plays a server timeline's interface keys itself, every frame, so a gui fades smoothly even
though the server started it. A timeline a client made plays on that client only, every frame.

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

A script inside an `Actor` has two more: `task.desynchronize()` moves it to a worker thread, where it
runs at the same time as other actors, and `task.synchronize()` brings it back. See
[Parallel Luau](parallel.md).

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
