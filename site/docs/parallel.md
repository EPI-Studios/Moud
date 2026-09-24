# Parallel Luau

Every script runs on one thread by default, one after the other. A script inside an `Actor` can
move part of its work to another thread, where it runs at the same time as the scripts inside other
actors. This page covers actors, switching a script between the serial and the parallel phase, what
a script may do in parallel, sending a message to an actor, and shared tables, which every actor can
read and write at once.

## An actor is a VM of its own

An `Actor` is a `Model` whose scripts run in a Luau VM of their own. You create one the way you
create anything else, and put the scripts that belong to it inside it:

```lua
-- server
local guard = game.world:add("Actor", { name = "guard" })
guard:add("Script", { name = "brain", source = "res://server/guard.luau" })
```

- A script belongs to the closest actor above it. A script with no actor above it runs where every
  other script runs.
- All the scripts inside one actor share its VM. They run one after the other, never at the same
  time as each other.
- Globals are per VM. A global set by a script in the actor is not seen outside it, and the other
  way round.
- `require` loads a module once per VM. An actor that requires a module gets its own copy of it,
  with its own state.
- `script:getActor()` is the actor a script belongs to, or `nil`.
- Destroying an actor stops its scripts and closes its VM.

Putting a script in an actor changes nothing on its own. It runs on the main thread like any other
script until it asks to run in parallel.

## Serial and parallel

`task.desynchronize()` waits for the next parallel phase and carries on from there, on a worker
thread. `task.synchronize()` waits for the serial phase that follows and carries on from there, on
the main thread:

```lua
-- server, a Script inside an Actor
local body = script.parent:find("body")

while true do
    task.desynchronize()
    local target = nearestEnemy(body.position)    -- reading, in parallel
    task.synchronize()
    if target then body.cframe = cframe(body.position, target.position) end
    task.wait(0.2)
end
```

- Both yield, so they run inside a script or a function started with `task.spawn`, like
  `task.wait`.
- `task.desynchronize()` while already in parallel returns at once, and so does `task.synchronize()`
  while already serial.
- Both are errors in a script that is not inside an actor.
- `task.wait` in parallel carries on in parallel once the time has passed.

Scripts inside different actors run at the same time in the parallel phase, each on a worker
thread. Scripts inside the same actor still take turns. Split work over several actors to spread it
over the cores: one per enemy or one per chunk, not one for everything.

## When the phases run

The parallel phase runs once a tick on the server and once a frame on a client, after
`game.stepped` and `game.renderStepped` have fired:

| | |
|---|---|
| 1 | scripts start, waits that are over resume, `game.stepped` or `game.renderStepped` fires |
| 2 | messages sent since the last phase reach their `bindToMessage` handlers |
| 3 | **the parallel phase**: every script that desynchronized, every `connectParallel` handler and every `bindToMessageParallel` handler that is due |
| 4 | scripts that called `task.synchronize()` in the parallel phase carry on |
| 5 | messages sent during the phase reach their `bindToMessage` handlers |

The main thread waits for the whole parallel phase to finish before step 4, so nothing serial runs
while a worker does.

A script that desynchronizes in step 4 or 5 runs in the next tick's parallel phase.

## What works in parallel

A script in parallel can read the world but not change it. Writing a property, or calling anything
that changes something outside the script's own VM, is an error that tells you to synchronize:

```lua
task.desynchronize()
print(door.transparency)          -- fine
door.transparency = 0.5           -- Part.transparency cannot be set in parallel, call task.synchronize() first
door:destroy()                    -- Instance:destroy cannot be called in parallel, call task.synchronize() first
```

| Works in parallel | Is an error in parallel |
|---|---|
| reading properties, attributes, `name`, `parent` and children | writing any property or attribute |
| `find`, `children`, `descendants`, `findFirstDescendant`, `isA`, `hasTag`, `getAttribute`, `getActor` | `add`, `clone`, `destroy`, `addTag`, `setAttribute`, `query` |
| vectors, frames, rotations, `math`, `angle`, `random` | `connect`, `once`, `wait` on a signal, `connectParallel` |
| `print`, `clock`, `os`, `task.wait` | `task.spawn`, `task.delay` and the other `task` calls |
| `actor:sendMessage` | `require` |
| everything in [`sharedTable`](#shared-tables) | raycasts and other world queries, `game.persist`, `messaging`, stores |

- Whatever the right-hand column lists, the answer is to call `task.synchronize()` first, do it,
  and desynchronize again if there is more parallel work.
- Your own Luau is always fine: tables, closures, string work and loops run in parallel at full
  speed. The rules are about what the engine gives you.
- `print` in parallel is shown once the phase is over.

> [!WARNING]
> Queries and raycasts are serial only. Read the positions you need in parallel, and cast in the
> serial phase.

## Connecting in parallel

`connectParallel` connects a handler that runs in the parallel phase each time the signal fires,
without calling `task.desynchronize()` first:

```lua
-- server, a Script inside an Actor
local body = script.parent:find("body")

game.stepped:connectParallel(function(dt)
    local goal = pickGoal(body.position)          -- parallel
    task.synchronize()
    body.cframe = body.cframe:lerp(goal, dt * 4)  -- serial
end)
```

- Every signal has it, next to `connect`. It returns a `Connection` like `connect` does.
- The handler runs in the next parallel phase after the signal fires, not while it fires.
- It runs as its own thread, so it can `task.synchronize()` and `task.wait`.
- Only a script inside an actor can use it, and only while it is serial.
- It stops with the script that connected it, like any connection.

## Messages

`sendMessage` hands an actor a message under a topic. Scripts inside the actor bind a handler to
the topic, and the handler runs later with the message's values:

```lua
-- server, a Script inside the "guard" Actor
script.parent:bindToMessage("alert", function(from, position)
    print(from, "saw something at", position)
end)
script.parent:bindToMessageParallel("path", function(goal)
    local route = planRoute(goal)                 -- parallel
    task.synchronize()
    walk(route)
end)
```

```lua
-- server, anywhere
game.world.guard:sendMessage("alert", "tower", vec3(10, 64, 4))
game.world.guard:sendMessage("path", vec3(0, 64, 0))
```

- `sendMessage` returns at once. It works serial and in parallel, from any script.
- `bindToMessage` handlers run serially; `bindToMessageParallel` handlers run in the parallel phase.
  Both run as their own thread, so both can wait.
- Every handler bound to the topic gets the message, in the order they were bound.
- Only a script inside the actor can bind to its messages, and only while serial. Binding returns
  a `Connection`.
- A message carries numbers, text, booleans, `nil`, vectors, frames, colours, instances, shared
  tables, and tables of those. A table arrives as a copy. A function is an error.
- A message nobody is bound to when it arrives is dropped.

## Shared tables

A shared table is a table every VM sees at once. A write from one actor is there for every other
actor straight away, in parallel or not:

```lua
local scores = sharedTable.named("scores")    -- the same table for every script on this side

scores.red = 0
sharedTable.increment(scores, "red", 1)       -- returns the old value
sharedTable.update(scores, "log", function(old)
    return (old or "") .. "red scored\n"
end)

for team, score in scores do print(team, score) end
```

- `sharedTable(t)` makes a new one from a table, and `sharedTable()` an empty one. A table inside
  it becomes a shared table too.
- `sharedTable.named(name)` is one shared table per name, made the first time it is asked for. It
  is how actors find each other's data without passing it around. Sending a shared table in a
  message works too, and it arrives as the same table, not a copy.
- Keys are strings or whole numbers from 0. Values are what a message carries, without plain
  tables: a table assigned into a shared table becomes a shared table. A function is an error.
- Setting a key to `nil` removes it.
- `for key, value in st` walks what the table held when the loop started.

| Function | What it does |
|---|---|
| `size(st)` | how many keys it has |
| `clear(st)` | removes every key |
| `increment(st, key, delta)` | adds `delta` to a number that is there, in one step, and returns the value before |
| `update(st, key, fn)` | calls `fn` with the value, or `nil`, and stores what it returns, in one step |
| `clone(st, deep)` | a copy. A shallow copy shares the shared tables inside it, a deep one copies them too |
| `cloneAndFreeze(st, deep)` | a copy nobody can write |
| `isFrozen(st)` | whether it is one of those |

- Reading or writing one key is safe from any number of actors at once. A read followed by a write
  is not: another actor can write in between. Use `increment` or `update` when the new value
  depends on the old one.
- `update` may call `fn` more than once when another actor writes the key at the same time, so keep
  `fn` free of anything else.
- Writing a frozen table is an error: "this SharedTable is frozen".

> [!NOTE]
> Shared tables live on one side. The server has its own and each client has its own, like
> `messaging`. Use a channel to reach the other side. See
> [Talking across the boundary](talking.md).
