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
- During a playtest in the editor, it goes back to editing.
- Otherwise it leaves to the project hub.

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

Before a place's scripts can run, the world has to load. That means there is no script to configure
the loading screen from, so you describe it in `place.toml`:

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
table is missing.

To fade in once the place is running, cover the screen with a `ScreenGui` in the start scene and tween
it away from `client/main.luau`.

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
and **Quit**, instead of Minecraft's screen.

## Screens an exported game never shows

An exported game starts straight in its place. It never shows Minecraft's title screen, singleplayer
world list, multiplayer server list or Realms. Leaving the place closes the game.
