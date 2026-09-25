# Pausing, quitting, settings and screens

A place draws its own pause menu, its own settings screen and its own loading screen instead of
Minecraft's. The globals that do it are `settings` and `ui`, along with a few members of `game`. They
read and write one player's own game, so you use them from `client/` scripts, the same place `camera`
and `input` live.

## Escape and pausing

`game.pauseRequested` is a signal that fires when the player presses <kbd>Escape</kbd>, or when the
game window loses focus. You connect to it to open your own menu:

```lua
local menu = world:find("PauseMenu") :: ScreenGui

game.pauseRequested:connect(function(reason)
    -- reason is "escape" or "focusLost"
    if reason == "focusLost" and menu.enabled then return end
    menu.enabled = not menu.enabled
    game.paused = menu.enabled
    if menu.enabled then input:releaseMouse() else input:lockMouse() end
end)
```

- `reason` is the string `"escape"` or `"focusLost"`, so you can treat the two differently. The line
  above leaves an already open menu alone when the player alt-tabs away, and only toggles it on a real
  <kbd>Escape</kbd>.
- `game.paused = true` freezes the world the way the vanilla pause menu does: the server stops
  ticking, and bodies and physics stop. Client scripts keep running, so your menu can still animate.
  It only freezes a single player game. On a server with other players, writing it does nothing.
- `input:releaseMouse()` gives the pointer back so the player can click the menu, and
  `input:lockMouse()` hands it to the camera again when the menu closes.

With no script connected, <kbd>Escape</kbd> opens the pause menu as before: Minecraft's own while you
are developing, and a plain Resume / Settings / Quit menu in an exported game. The `pauseMenu` feature
in `place.toml` turns that off entirely.

> [!IMPORTANT]
> If any script is connected to `game.pauseRequested`, the vanilla pause menu does not open. Your
> connection is the pause menu, so if it does not show anything, <kbd>Escape</kbd> appears to do
> nothing.

## Quitting

`game:quit()` leaves the place. Where the player ends up depends on how the place is running:

```lua
game:quit()
```

- In an exported game, the game closes.
- In a place started from the command line with `-Pplace`, the game closes too.
- During a playtest in the editor, it goes back to editing.
- In a project opened from the project hub, it leaves to the hub.

The game waits for any `window:screenshot` still being written before it closes, so a script can take
a picture and quit on the next line.

`game.exported` is true inside an exported game. Read it when a menu should offer **Quit to desktop**
in the shipped game and **Back to the hub** while you work on it.

`game:bindToClose(handler)` runs something as the place stops, on either side. See
[Tweens and timing](tweens-and-timing.md#when-the-place-stops).

## Settings

`settings` reads and writes the player's own Minecraft options, so a place can build its own settings
screen instead of sending them to Minecraft's. Changes apply straight away, and `settings:save()`
keeps them for next time.

```lua
settings.fov = 90                  -- 30 to 110
settings.sensitivity = 0.5         -- 0 to 1
settings.fullscreen = true
settings.vsync = false
settings.maxFps = 144              -- 10 to 260, 260 is unlimited
settings.guiScale = 3              -- 0 is automatic
settings.renderDistance = 12       -- 2 to 32 chunks
```

Volumes and key binds are lists rather than fixed properties, because Minecraft has several of each:

```lua
settings:volumes()                 -- { "master", "music", "record", "weather", "block", ... }
settings:volume("master")          -- 0 to 1
settings:setVolume("music", 0.3)

settings:actions()                 -- { "forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use", "chat", "playerList" }
settings:keyOf("jump")             -- "space"
settings:bind("jump", "f")         -- a letter, a digit, space, enter, tab, f1 to f12, the arrows,
                                   -- leftshift, rightcontrol..., mousebutton1 to mousebutton3
settings:save()

game:openSettings()                -- Minecraft's own options screen, for everything else
```

- `settings:volumes()` and `settings:actions()` give you the names to build a screen out of, so you
  can loop over them instead of writing a row per option by hand.
- `settings:keyOf(action)` tells you what a **Press a key** row should currently show, and
  `settings:bind(action, key)` changes it.
- `game:openSettings()` opens Minecraft's own options screen. Use it for everything `settings` does
  not cover, rather than rebuilding all of Minecraft's options.

## The core interface

`ui` turns the pieces of Minecraft's own interface on and off for this client, while the place runs.

```lua
-- client
ui.setCoreGuiEnabled("hotbar", false)
ui.setCoreGuiEnabled("all", false)
print(ui.getCoreGuiEnabled("chat"))
```

| Name | What it hides | The `[features]` switch it flips |
|---|---|---|
| `health` | the hearts | `healthBar` |
| `hunger` | the drumsticks | `hungerBar` |
| `hotbar` | the item bar | `hotbar` |
| `chat` | the chat window | `chat` |
| `playerList` | the Tab list | `tabList` |
| `crosshair` | the crosshair | `crosshair` |
| `experience` | the xp bar and level | `xpBar` |
| `all` | all seven at once | |

- A name that is not one of these is an error that lists the ones that are.
- `getCoreGuiEnabled("all")` is true only while every one of the seven is on. One hidden piece makes
  it false.
- Reloading the place puts every one it touched back the way `place.toml` asked for it.

> [!TIP]
> These are the same switches `[features]` in `place.toml` sets. Use `place.toml` to say what the
> place looks like for everyone, and `ui` to change it for one client, for a cutscene or a menu. See
> [Places](place.md#placetoml).

## Notifications

`ui.sendNotification` shows a toast in the top right corner of this client's screen:

```lua
-- client
ui.sendNotification({
    title = "Wave 3",
    text = "Hold the gate",
    icon = "res://ui/wave.png",
    duration = 8,
    button1 = "Ready",
    button2 = "Later",
    callback = function(button) print(button, "was pressed") end,
})
```

The toast fades over its last second and goes after `duration` seconds, 5 by default. Several stack
down the corner in the order they were sent.

| Key | | |
|---|---|---|
| `title` | required | one line |
| `text` | optional | wraps under the title |
| `icon` | optional | a `res://` image, drawn 32 by 32 on the left |
| `duration` | optional | seconds, more than 0 |
| `button1`, `button2` | optional | a button each, along the bottom |
| `callback` | optional | gets the **text** of the button that was pressed |

- A notification with no title is an error, and so is a duration of 0 or less.
- The callback only runs when a button is pressed, and it is handed the button's text, so the example
  above prints `Ready` or `Later`. A toast that runs out of time, or that a reload clears, never calls
  it.
- Pressing either button closes the toast.
- The toast is built out of Moud's own `ScreenGui`, `Frame`, `TextLabel` and `TextButton` instances on
  a local screen, so it draws over the place's own interface and disappears with it on a reload.

<!-- demo:game-and-screens-toast -->

## The loading screen

A place draws its own loading screen with `client/loading.luau`. It runs on the player's machine the
moment the game knows which place it is starting: before it looks up the server, before it connects,
before there is a world. What it builds is drawn over Minecraft's connecting, loading and saving
screens, and stays until the place is running.

```lua
-- client/loading.luau
local world = game.world
local loading = game.loading

local gui = world:add("ScreenGui", { name = "Loading", displayOrder = 100 })
local screen = gui:add("Frame", { size = udim2(1, 0, 1, 0), backgroundColor = color(1, 1, 1) })
screen:add("UIGradient", { startColor = color(0.14, 0.1, 0.28), endColor = color(0.02, 0.03, 0.07), rotation = 90 })

screen:add("TextLabel", {
    position = udim2(0.5, 0, 0.5, -50), anchorX = 0.5, anchorY = 0.5,
    size = udim2(0, 400, 0, 40), backgroundTransparency = 1,
    text = "CRYSTAL RUN", textSize = 24, textColor = color(1, 1, 1),
})

local bar = screen:add("Frame", {
    position = udim2(0.5, 0, 0.5, 10), anchorX = 0.5, anchorY = 0.5, size = udim2(0, 300, 0, 6),
    backgroundColor = color(1, 1, 1), backgroundTransparency = 0.85, cornerRadius = 3,
}) :: Frame
local fill = bar:add("Frame", { size = udim2(0, 0, 1, 0), backgroundColor = color(0.55, 0.8, 1), cornerRadius = 3 })
local status = screen:add("TextLabel", {
    position = udim2(0.5, 0, 0.5, 22), anchorX = 0.5, size = udim2(0, 400, 0, 16),
    backgroundTransparency = 1, textSize = 9, textColor = color(0.75, 0.8, 0.95),
}) :: TextLabel

local words = {
    lookup = "Finding a server",
    connecting = "Connecting",
    joining = "Joining",
    world = "Loading the world",
    ready = "Ready",
}

local function show()
    fill:tween({ size = udim2(loading.progress, 0, 1, 0) }, { time = 0.2 })
    status.text = words[loading.phase]
end

loading.changed:connect(show)
show()

local panel = screen:add("Frame", {
    position = udim2(0.5, 0, 0.5, 30), anchorX = 0.5, anchorY = 0.5, size = udim2(0, 300, 0, 90),
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.4, cornerRadius = 6, visible = false,
}) :: Frame
local reason = panel:add("TextLabel", {
    position = udim2(0, 12, 0, 10), size = udim2(1, -24, 0, 40), backgroundTransparency = 1,
    textColor = color(1, 1, 1), textWrapped = true,
}) :: TextLabel
local retry = panel:add("TextButton", { position = udim2(0, 12, 1, -34), size = udim2(0.5, -16, 0, 24), text = "Try again" }) :: TextButton
local quit = panel:add("TextButton", { position = udim2(0.5, 4, 1, -34), size = udim2(0.5, -16, 0, 24), text = "Quit" }) :: TextButton

local update = false

loading.failed:connect(function(why, kind)
    update = kind == "update"
    reason.text = why
    retry.text = if update then "Download" else "Try again"
    panel.visible = true
    bar.visible = false
    status.visible = false
end)

retry.activated:connect(function()
    if update then
        loading:openDownload()
        return
    end
    panel.visible = false
    bar.visible = true
    status.visible = true
    loading:retry()
end)

quit.activated:connect(function()
    loading:quit()
end)
```

- The script has its own `game.world`, which only it sees. Nothing in it is sent to the server, and
  the place's scripts do not find it in their tree. It has the interface classes, tweens, `task`,
  `Sound`, `VideoFrame`, `require` and `window`. It has no players, no remotes and no `camera` or
  `input`: none of them exist yet.
- It shows the moment Minecraft has loaded its resources. Minecraft's own fade out is skipped, so a
  server that answers fast never fails behind the Mojang screen.
- `task.wait`, `task.delay` and tweens count real seconds, so they keep time while the window is in
  the background and drawing slowly.
- `loading.changed` fires whenever the phase, the progress, the message or the server changes, so the
  bar above follows along without polling.
- Connecting to `failed` means the script draws the failure itself: the engine's own screen with
  **Try again** and **Quit** is not shown. The pointer is free on a failure, so the buttons can be
  clicked. A script that does not connect to it keeps the engine's screen.
- A script that errors before it finishes its first run is stopped, and the `[loading]` look below
  takes its place.

`game.loading` is there in `client/main.luau` too:

| Member | |
|---|---|
| `phase` | `"lookup"` while the server lookup is read, `"connecting"` while it connects to a server or starts its own world, `"joining"` while it logs in, `"world"` while the world and the place arrive, then `"ready"` |
| `progress` | from 0 to 1. It follows the world loading where Minecraft reports it, and steps with the phase otherwise |
| `message` | the lookup's `message`, or why it failed |
| `server` | the address being tried, empty when there is none |
| `hold` | `false` by default. See below |
| `changed` | fires when any of the above changes |
| `ready` | fires once the place is running |
| `failed` | fires with the reason and what went wrong: `"connect"`, `"update"`, `"closed"` or `"disconnected"` |
| `finish()` | takes the loading screen away |
| `retry()` | looks the server up and connects again, after a failure |
| `quit()` | closes the game |
| `openDownload()` | opens the lookup's `download` page in the browser |

The reason is short and plain, like `Connection refused`, `Timed out` or `The server is full`. What
Minecraft said exactly is in the log. A kick keeps the text the server gave it.

### Keeping it up until the game is ready

When the place is running, the loading screen fades out and its script stops. A game that wants to
load more first, or play an intro under it, sets `hold`, and takes it away itself with `finish()`:

```lua
-- client/loading.luau
game.loading.hold = true
```

```lua
-- client/main.luau
local music = game.world:add("Sound", { soundId = "res://sounds/theme.ogg", looped = true }) :: Sound

music.loaded:once(function()
    game.loading:finish()
    music:play()
end)
```

`finish()` fades the loading screen out over less than half a second, then stops its script. Until
then it is drawn over the game. A place that sets `hold` and never calls `finish()` stays on its
loading screen.

When the player is disconnected later on, the script starts again and gets `failed` with
`"disconnected"`, so the same panel shows why and offers to rejoin.

### Without a script

A place with no loading script describes its loading screen in `place.toml` instead:

```toml
[loading]
background = "res://ui/loading.png"   # covers the screen, cropped to fit
color = "#12151c"                     # behind the background, and alone without one
logo = "res://ui/logo.png"            # in the middle; without one the place name is shown
text = "Loading"                      # animated dots are added
tips = ["Crystals respawn after 10 seconds", "Double jump off walls"]   # one every 5 seconds
```

It replaces Minecraft's world loading, connecting and saving screens. While you are developing it
shows once a `[loading]` table exists. An exported game always uses it, with the defaults when the
table is missing. With a loading script, only `color` is used, as the colour behind it.

## Being kicked or disconnected

The server decides who stays. Both calls take the reason the player is shown:

```lua
-- server
player:kick("The round is over")
player:ban("Flying", 3600)
```

`ban` takes the reason and a number of seconds. It also writes the player into the Minecraft server's
ban list, so they cannot come straight back. See
[Players and bodies](players.md#arriving-spawning-and-respawning).

In an exported game, a disconnected player sees the reason over the loading look, with **Try again**
and **Quit**, instead of Minecraft's screen. A [loading script](#the-loading-screen) connected to
`game.loading.failed` draws that screen itself.

## Screens an exported game never shows

An exported game starts straight in its place. It never shows Minecraft's title screen, singleplayer
world list, multiplayer server list or Realms. Leaving the place closes the game.
