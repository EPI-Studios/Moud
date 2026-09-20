# Players and bodies

A player is the person connected to the server. Their body is a `Character` instance whose `owner`
property holds their player id, and it is the body that most of this page addresses: the calls that
find someone, measure a distance or walk a body around all take and return bodies. The player object
itself is what you use for things the body does not have, such as spawning, teams, leaderstats and
kicking.

## Arriving, spawning and respawning

Players arrive on a **`SpawnLocation`**: a part you place in the editor (Explorer > Insert >
SpawnLocation, 6 by 1 by 6 by default). A player stands on its top, facing where its front faces.
With several enabled, each spawn picks one at random from those the player's [team](#teams) may use.
Turn one off with `enabled = false`. One inside a [storage container](containers.md) is never used.
With none at all, players arrive at (0.5, 70, 0.5).

Minecraft's own world spawn follows the first enabled `SpawnLocation` in the world, at the middle of
its top face. The engine moves it whenever that location moves, so anything in the game that uses
the world spawn sends players there too.

By default every player gets a body as soon as they join. Two properties change that:

```lua
-- server
game.players.autoSpawn = false      -- default true
game.players.respawnTime = 3        -- seconds after dying, default 5

game.players.joined:connect(function(player)
    -- with autoSpawn off the player has no body yet: player.character is nil,
    -- they wait at a spawn location, unable to move or jump
    task.delay(2, function()
        player:spawn()                          -- on a SpawnLocation
        -- player:spawn(vec3(10, 66, 4))        -- or exactly here
    end)
end)
```

- `player:spawn()` gives a player without a body a new one. A player whose body is dead gets a
  fresh body. A living body is moved.
- With `autoSpawn` on, a body whose `humanoid` dies gets a new one after `respawnTime`. With it off,
  a dead body stays until a script calls `player:spawn()`.

> [!NOTE]
> `autoSpawn` and `respawnTime` go back to their defaults when the scripts reload. Set them at the
> top of `server/main.luau` so a save puts them back where you want them.

`game.players.spawned` fires every time a player gets a new body: the first one right after
`joined`, then after each respawn or `player:spawn()` that made a new body. A body that was only
moved does not fire it. That makes it the place to set up a fresh body, because everything you did to
the last one is gone:

```lua
game.players.spawned:connect(function(player)
    local humanoid = (player.character :: Character).humanoid
    humanoid.walkSpeed = 8
end)
```

A main menu keeps `autoSpawn = false`, shows its screen, and calls `player:spawn()` when Play is
pressed.

### Removing a player

`player:kick(message)` disconnects a player with a reason, see
[Pausing, quitting, settings and screens](game-and-screens.md).

`player:ban(reason, seconds)` writes them into the Minecraft server's own ban list and disconnects
them with the reason. They cannot come back until it runs out.

```lua
player:ban("Flying", 3600)     -- an hour
player:ban("Cheating")         -- forever, and 0 seconds means the same
player:ban()                   -- "You were banned"
```

> [!IMPORTANT]
> The ban outlives the place. It is the same list the `/ban` command writes, so it holds through a
> reload and a restart, and it is lifted with `/pardon`. A ban you write while testing is still there
> tomorrow.

## Teams

A `Team` is an instance with a colour. Every player is on one team or on none. You create teams in a
server script like any other instance:

```lua
-- server/main.luau
local red = game.world:add("Team", { name = "red", teamColor = color(1, 0.25, 0.25) })
local blue = game.world:add("Team", { name = "blue", teamColor = color(0.25, 0.45, 1) })
```

| Property | Default | |
|---|---|---|
| `teamColor` | white | the colour spawn locations are matched against |
| `autoAssignable` | true | new players may be put on this team |

When a player joins, they are put on the `autoAssignable` team with the fewest players, picked at
random between teams of the same size. With no such team they join none. A team inside a
[storage container](containers.md) is never picked.

Turn `autoAssignable` off on a team players are only meant to reach through a script or a pad, such as
a spectator team.

<!-- demo:players-teams -->

### Choosing a team from a script

`player.team` is a `Team` or `nil`. Any script can read it; only a server script can set it.

```lua
-- server
game.players.joined:connect(function(player)
    if player.name == "Meek" then
        player.team = red
        player:spawn()
    end
end)
```

- Setting it fires `playerRemoved` on the old team and `playerAdded` on the new one. Setting the team
  a player is already on does nothing.
- It does not move the body. The team counts from the next spawn, which is why the example calls
  `player:spawn()`: with `autoSpawn` on, the first body is already placed when `joined` fires.
- `player.team = nil` takes a player off every team.
- A destroyed team leaves its players on no team, and fires nothing.
- A player who leaves is taken off their team, and it fires `playerRemoved`.

### Spawning on your team

A `SpawnLocation` has three more properties for deciding who may use it:

| Property | Default | |
|---|---|---|
| `neutral` | true | anyone spawns here. Off: only the team whose `teamColor` matches this one's |
| `teamColor` | white | the team this location is for, when `neutral` is off |
| `allowTeamChangeOnTouch` | false | standing on it moves the player to the team of its colour |

A player spawns on a random enabled location that is `neutral` or has their team's colour. When none
of them fits, any enabled location will do. A player on no team only fits the neutral ones.

The colours have to be exactly the same, so give the location the team's own colour rather than
typing the numbers twice:

```lua
-- server/main.luau
game.world:add("SpawnLocation", {
    name = "redBase",
    position = vec3(-40, 65, 0),
    neutral = false,
    teamColor = red.teamColor,
    color = red.teamColor,
})
game.world:add("SpawnLocation", {
    name = "blueBase",
    position = vec3(40, 65, 0),
    neutral = false,
    teamColor = blue.teamColor,
    color = blue.teamColor,
})
```

`teamColor` is what the match is made against, and `color` is what the pad looks like. Setting both to
`red.teamColor` makes the base look like the team it belongs to.

### Changing team by standing on a pad

A location with `allowTeamChangeOnTouch` on, `neutral` off and `enabled` on moves anyone standing on
its top to the team with its `teamColor`. It checks every tick, so walking onto it is enough. It
works for any team with that colour, `autoAssignable` or not, and does nothing when no team has it.

A lobby with a neutral location to arrive on and a pad for each team:

```lua
-- server/main.luau
game.world:add("SpawnLocation", { name = "lobby", position = vec3(0, 65, 20) })

game.world:add("SpawnLocation", {
    name = "joinRed",
    position = vec3(-6, 65, 28),
    neutral = false,
    teamColor = red.teamColor,
    color = red.teamColor,
    allowTeamChangeOnTouch = true,
})
game.world:add("SpawnLocation", {
    name = "joinBlue",
    position = vec3(6, 65, 28),
    neutral = false,
    teamColor = blue.teamColor,
    color = blue.teamColor,
    allowTeamChangeOnTouch = true,
})
```

A pad is still a spawn location, so a red player can respawn on `joinRed` as well as on `redBase`.
Changing team on a pad does not move the body either.

<!-- demo:players-spawn -->

### Who is on a team

`team:getPlayers()` gives every `Player` on the team on the server. On a client it gives only the
local player, and only when they are on it.

`playerAdded` and `playerRemoved` fire on the server with the `Player`. A server that keeps a count
every client can read:

```lua
-- server/main.luau
local count = game.world:add("StringValue", { name = "teamCount" })

local function recount()
    count.value = "red " .. #red:getPlayers() .. "  blue " .. #blue:getPlayers()
end

for _, team in { red, blue } do
    team.playerAdded:connect(recount)
    team.playerRemoved:connect(recount)
end
```

The `StringValue` sits in the world, so it replicates, and a client script reads `count.value` to
label its interface without asking the server for anything.

Every body carries its wearer's team as `team`, so a client reads anyone's team off their body:

```lua
-- client
local me = game.players:me() :: Character
if me.team then print("I am on", me.team.name) end

for _, body in game.players:all() do
    local other = body :: Character
    if other ~= me and other.team == me.team then
        -- a teammate
    end
end
```

`player.team` on a client reads the same thing, for the local player.

> [!IMPORTANT]
> Set `player.team` on the server rather than `team` on a body. A player's body is put back to their
> team every tick, so a write to the body is undone before the next one.

## Leaderstats

Every player has a `leaderstats`, a container the engine makes for them when they join. Put value
instances in it and they show up beside their name in the Tab list.

```lua
-- server/main.luau
game.players.joined:connect(function(player)
    local stats = player.leaderstats :: Leaderstats
    stats:add("NumberValue", { name = "Kills" })
    stats:add("NumberValue", { name = "Deaths" })
end)

game.players.spawned:connect(function(player)
    local body = player.character :: Character
    body.humanoid.died:connect(function()
        local stats = player.leaderstats :: Leaderstats
        local deaths = stats:find("Deaths") :: NumberValue
        deaths.value += 1
    end)
end)
```

- The stats are made on `joined`, once, because the container is already there and only needs
  filling.
- The death counter is connected on `spawned`, because each body has its own `humanoid` and the
  connection to the old one went away with it.
- `deaths.value += 1` is enough to change what every player sees in the Tab list on the next frame.

Only `NumberValue`, `StringValue` and `BoolValue` children are read. A number is shown whole when it
is whole and to two decimals otherwise, a flag as `yes` or `no`.

- The stats outlive the body: dying and respawning does not touch them. They are destroyed when the
  player leaves, and made again empty if they come back.
- They sit under a `Leaderboard` at the root of the world, one `Leaderstats` per player, named after
  the player and carrying their id as `owner`. A player who changes name keeps the same instance.
- The `Leaderboard` is **out of the world**: it is not drawn, not found by queries, and not saved
  into a scene, the same as `ServerStorage`. See [Containers](containers.md).
- It replicates to every client all the same, and it replicates whole. The
  [streaming radius](place.md#streaming) does not cut it off, so any client can read anyone's score.
- Only the server should write them. A client may read them.

### In the Tab list

Holding Tab shows each player's values after their name, and sorts the list by the **first**
`NumberValue` in their stats, highest first. Players without one fall to the bottom. Nothing else
about the list changes, and `tabList = false` in `place.toml` still hides it entirely.

> [!TIP]
> The order of the children is what the list is ranked by, so add the score you want players ranked
> on before any other `NumberValue`.

<!-- demo:players-leaderstats -->

### Reading someone else's

On the server, go through the player object:

```lua
-- server
local other = game.players:playerOf(body)
if other then
    local kills = (other.leaderstats :: Leaderstats):find("Kills") :: NumberValue
    print(other.name, kills.value)
end
```

On a client, read the board out of the tree:

```lua
-- client, straight off the tree
local board = game.world:find("Leaderboard")
for _, stats in (board :: Instance):children() do
    print(stats.name, (stats:find("Kills") :: NumberValue).value)
end
```

`player.leaderstats` on a client is the local player's own. Everyone else's is read off the board,
where `owner` is the player id `body.owner` carries.

## Controls

A player's controls are three switches: `move` (walking, strafing and sprinting), `jump` and
`look` (turning with the mouse). Turning one off is how you hold a player still during a cutscene or
while a menu is open.

```lua
-- server: for one player
player.controls.move = false
player.controls:disable()           -- all three
player.controls:enable()

-- client: for yourself
game.players.controls.look = false
```

A control works only when both the server and the client leave it on. Scripted walking
(`walkTo`, `follow`) still moves a body whose controls are off, so a cutscene can walk the player.
Client switches go back on when the scripts reload; server switches when the player leaves or the
scripts reload.

The body's `humanoid` can take controls away too: `sit` takes `move`, `platformStand` takes `move`
and `jump`, and turning off its `running` or `jumping` state takes `move` or `jump`. A control then
works only when the server, the client and the humanoid all leave it on. A dead body's humanoid
holds nothing. See [Movement](movement.md#state).

## Finding players

`game.players` has a call for most of the questions a place asks about who is where. They go through
the tree's list of characters, not every instance in the world, and only count bodies a player is
wearing.

```lua
local p = game.players
p:all()                                   -- every body
p:count()
p:near(position, 30, except)              -- within 30 m, nearest first
local body, distance = p:nearest(position, radius, except)
p:sortedByDistance(position)
p:inBox(cframe(0, 65, 0), vec3(10, 4, 10))
p:inPart(zonePart)
p:inCone(eye, lookDirection, 30, 50, me)  -- within 30 degrees of the direction, 50 m away
p:visibleFrom(eye, 50, me)                -- in range and nothing solid in the way
p:withTag("red")
p:random(except)
p:inRange(a, b, 10)
p:bodyOf(playerId)
p:fromName("Meek")                        -- not case sensitive
p:me()                                    -- a client's own body
```

`except` leaves one body out, usually the asker's own. Pass it whenever the question is about other
people; leave it out and an explosion finds the player who set it off.

<!-- demo:players-queries -->

## Asking a body

Once you have a body, these answer questions about it without you doing the maths:

```lua
body:distanceTo(other)
body:distanceSqTo(other)                  -- no square root, for comparing
body:canSee(other, 60)                    -- eyes to eyes, parts and blocks block it
body:facing(other, 45)                    -- within 45 degrees of where the head looks
body:lookDirection()
body.velocity                             -- metres a second
body:isGrounded()
body:isInWater()
body:isMoving()
```

Use `distanceSqTo` when you only compare distances against each other, since it skips the square
root. Use `distanceTo` when you show the number or compare it against a range in metres.

## Moving bodies

`walkTo`, `follow`, `jump` and `stopWalking` work on any body. A body with no player is walked by
the engine through its `humanoid`. A body a player is wearing is walked by that player's own
client: the server sends the path and the client presses the keys for them, without turning their
camera. Pressing a movement key hands the controls back, which ends the walk on the server too.
`humanoid.arrived` fires either way.

```lua
local ok = npc:walkTo(vec3(20, 65, 4))    -- along a path around what is in the way; false when there is none
npc:follow(targetBody, 3)                 -- keeps following, stopping 3 m away, re-planning as it moves
npc:stopWalking()
npc:isWalking()
npc:jump()
npc:lookAt(position)
npc:face(direction)
```

`walkTo` answers false straight away when there is no path, so check what it returns before you wait
on `humanoid.arrived`.

You can also ask for a path without walking anything along it:

```lua
local corners = game.path:find(from, to)                    -- nil when there is no way
local closest = game.path:find(from, to, { partial = true }) -- as close as it gets instead of nil
game.path:isReachable(from, to)
local wander = game.path:randomPointNear(npc.position, 12)
npc:walkTo(point, { partial = true })
```

`partial = true` is what you want for a wandering npc, which should set off in roughly the right
direction rather than stand still because the target is unreachable.

Paths run on a navmesh built with Recast and walked with Detour. It is made from the level's blocks and every part that collides, sized for a player: 1.8 tall,
0.3 wide either side of the middle, climbing one block, walking slopes up to 50 degrees. It is built
in 32 by 32 block tiles the first time a path crosses them, and a tile is built again when a block in
it changes or a part in it moves. A path is the corners a body turns at, not every step.

A walking body keeps to the ground under it the whole way, so it rises onto a step and goes down a
slope between two corners instead of cutting a straight line through the air. It turns toward where
it goes at a person's pace. A follower stops at its distance, sets off again once the target is a
step and a half further, and walks as close as it can get when the target stands somewhere it cannot
reach. A player walked by the server jumps when they walk into a step.

## The player object

`game.players.joined` and `.leaving` hand over a player object (server):

```lua
game.players.joined:connect(function(player)
    player:spawn(vec3(0, 66, 0))
    print(player.name, player.character, player:ping(), player:viewTime())
end)
```

- `player.name` is the name the player connected under.
- `player.character` is their body, or nil while they have none.
- `ping()` is the round trip in seconds.
- `viewTime()` is the server time the player was looking at, for `game.history:rewind`.
