# Camera and input

`camera` and `input` are two globals that exist only in `client/main.luau`. `camera` is the instance
the world is drawn from for the player running that script, and `input` is how you read that player's
keyboard, mouse and gamepad. There is one camera per client and it is local: it never crosses to
anyone.

Neither global exists on the server, so everything on this page belongs in `client/main.luau` or in a
module a client script requires.

## Modes

`camera.mode` decides who places the camera each frame:

```lua
camera.mode = "firstPerson"   -- from the player's own eye
camera.mode = "thirdPerson"   -- pulled back along the look by `distance`
camera.mode = "scriptable"    -- wherever `cframe` says, roll included
```

In the two follow modes **aiming stays the player's**. Their mouse turns them, with their own
sensitivity and every accessibility option attached to it. Where the camera *is* is yours, and that is
the whole of third person.

```lua
camera.fov = 80                   -- 0, the default, is the player's own fov setting
camera.distance = 6
camera.offset = vec3(0, 0.2, 0)
```

- **`fov`** is the field of view in degrees. It starts at 0, which means the camera uses whatever
  field of view the player set in their own options. Write a number to take it over, and write 0 to
  hand it back.
- **`distance`** is how far back `thirdPerson` pulls the camera along the player's look, in metres.
- **`offset`** shifts the camera from the point the mode picked for it, in metres. The line above
  lifts it 20 cm.

<!-- demo:camera-modes -->

## Following something

`camera.subject` is the instance the camera watches. It holds a reference, so the camera follows the
thing rather than a position copied out of it once:

```lua
camera.subject = world:find("statue")
camera.subject = nil            -- back to the player
```

Set it back to `nil` when you are done, or the camera keeps watching the statue while the player walks
away.

## Scriptable

In `scriptable` mode nothing writes the camera except you, and `camera.cframe` is where it goes. Only
this mode honours roll, because roll is something only a frame can carry:

```lua
camera.mode = "scriptable"
camera.cframe = cframe.lookAt(eye, target) * cframe.angles(0, 0, 0.2)
```

`cframe.lookAt` points the camera from `eye` at `target`, and multiplying by `cframe.angles(0, 0, 0.2)`
rolls it a fifth of a radian around its own look.

Set the camera on `game.renderStepped` when it has to move every frame. That signal runs just before
the frame is drawn, so the frame you write is the frame the world is drawn against.

## Tweening the camera

`camera` is an instance like any other, so it tweens:

```lua
camera.mode = "scriptable"
camera:tween({ cframe = cframe.lookAt(vec3(0, 80, 20), vec3(0, 65, 0)), fov = 50 }, { time = 2, easing = "sine", direction = "inOut" })
```

The first table is the properties to land on, the second is how to get there. See
[Tweens and timing](tweens-and-timing.md) for the easing names.

> [!IMPORTANT]
> `cframe` only moves the camera in `scriptable` mode. The two follow modes write it back every
> frame, so a tween of `cframe` outside `scriptable` is overwritten before anyone sees it. `fov`
> tweens in any mode.

## Cameras in a scene

Place `Camera` instances in the editor as shots to cut or fly to. With **Helpers** on, the viewport
draws each one as a view cone. Select one to get two buttons in Properties:

- **View from here** moves the editor camera to it.
- **Set to the view** moves it to where the editor camera is.

A scene camera does nothing on its own. A client script looks through it by copying its frame onto the
client's own camera:

```lua
local shot = game.world:find("MenuShot") :: Camera
camera.mode = "scriptable"
camera.cframe = shot.worldCframe
camera.fov = shot.fov
```

- `game.world:find("MenuShot")` looks the scene camera up by the name you gave it in the editor.
- `shot.worldCframe` is where that instance sits in the world, which is what `camera.cframe` wants.
- The mode has to be `scriptable` first, or the follow mode overwrites the frame on the next one.

## Camera paths

A `CameraPath` is a smooth curve through the `Attachment`s inside it, in Explorer order. The camera
moves along it at an even speed however far apart the points are.

| Property | |
|---|---|
| `duration` | seconds from start to end, 5 by default |
| `easing`, `direction` | like a tween, `sine` and `inOut` by default |
| `looped` | starts again instead of finishing |
| `closed` | joins the last point back to the first, for orbits |
| `faceAlong` | looks along the curve instead of turning between the points' own rotations |
| `lookAt` | an instance to keep looking at, which wins over `faceAlong` |

In the editor, select a path and use **Add point at the view** to drop an `Attachment` where the
editor camera is, and **Preview** to fly the editor camera along it. Right click or Escape stops the
preview. The curve is drawn in the viewport with **Helpers** on.

You fly the camera along a path with `camera:play`, which hands back a playback object:

```lua
local path = game.world:find("IntroPath") :: CameraPath
local playback = camera:play(path)                                -- the path's own duration and easing
-- camera:play(path, { duration = 8, easing = "linear", restore = false })

playback.finished:connect(function(completed)
    -- completed is false when it was stopped or the path was destroyed
end)
playback.progress   -- 0 to 1
playback.playing
playback:stop()
```

- Passing a table as the second argument overrides the path's own `duration`, `easing` and
  `direction` for this playback, leaving the instance alone.
- `camera:play` switches the camera to `scriptable` and, when the playback ends, back to the mode it
  had. Pass `restore = false` to leave it in `scriptable` instead.
- `finished` fires whether the path ran out or something stopped it, so read `completed` before you
  treat it as the end of a cutscene.
- Playing another path on the same camera stops the first one.

## Effects

The camera has its own verbs for shaking and knocking it about, on the camera rather than on some
global:

```lua
camera:shake(0.35)                  -- trauma, which decays
camera:kick(-2, 1, 0, 0.15)         -- pitch, yaw, roll, seconds
camera:fovPunch(3, 0.2)             -- degrees, seconds
camera:clearEffects()
```

- `shake` takes an amount of trauma that decays on its own, so you add to it on a hit instead of
  scheduling a stop.
- `kick` snaps the camera by the pitch, yaw and roll you give it and settles over the seconds you
  give it. Use it for recoil.
- `fovPunch` widens the field of view by that many degrees for that many seconds.
- `clearEffects` drops all of them at once, which is what you want before a cutscene.

They layer on top of whatever the mode decided, so a scripted camera still shakes.

## Screen and world

`worldToScreen` turns a point in the world into a point on the screen, which is how you put a label
over something:

```lua
local at = camera:worldToScreen(head.worldCframe.position)
if at == nil then
    -- behind the camera, and there is nowhere on screen to put it
end
```

`screenToRay` goes the other way, turning a point on the screen into a ray you can cast into the
world:

```lua
local from, direction = camera:screenToRay(x, y)
local part = world:raycast(from, direction, 30)
```

Both speak **gui-scaled pixels**, the same units `input.mouseX` answers in, so the three compose
without a conversion. The ray's direction is unit length, so stepping along it is in metres, and the
`30` above means 30 metres.

## Input

You read the player's controls by action name. A place that asks for `"jump"` keeps working when the
player rebinds the key, and it works on every keyboard layout:

```lua
input:down("forward")  input:down("back")   input:down("left")  input:down("right")
input:down("jump")     input:down("sneak")  input:down("sprint")
input:down("attack")   input:down("use")
```

Each call answers true while the player is holding the key bound to that action. An unknown action is
an error, not a key that never fires, so a typo shows up the first time the line runs.

The mouse and the screen it is measured against:

```lua
input.mouseX        -- where the pointer is, in gui-scaled pixels
input.mouseY
input.mouseDeltaX   -- how far it moved since the last frame
input.mouseDeltaY
input.screenWidth   -- what the two above are measured against
input.screenHeight
input.sensitivity   -- readable and writable
input.mouseLocked
input:lockMouse()
input:releaseMouse()
```

`input:releaseMouse()` keeps the pointer free until a script calls `input:lockMouse()`, even when a
loading screen or a menu closes, or the player clicks into the game. That is what a main menu wants
from its first frame.

The middle of the screen is nameable, which is the one point a place always wants:

```lua
local x = input.mouseLocked and input.screenWidth / 2 or input.mouseX
local y = input.mouseLocked and input.screenHeight / 2 or input.mouseY
```

While the mouse is locked the pointer is not somewhere you can read, so the crosshair in the middle is
what the player is pointing at. When it is free, `mouseX` and `mouseY` are.

## Input events

Every key, mouse button, scroll and mouse move reaches three events:

```lua
input.inputBegan:connect(function(object, gameProcessed)
    if gameProcessed then return end
    if object.keyCode == "f" then
        print("f went down with the pointer at", object.position.x, object.position.y)
    end
end)
input.inputChanged:connect(function(object, gameProcessed) end)
input.inputEnded:connect(function(object, gameProcessed) end)
```

`inputBegan` fires when something goes down, `inputEnded` when it comes back up, and `inputChanged`
while something keeps moving, such as the wheel or the mouse itself.

`object` is an `InputObject`:

| Member | |
|---|---|
| `inputType` | `keyboard`, `mouseButton1`, `mouseButton2`, `mouseButton3`, `mouseWheel`, `mouseMovement` or `gamepad` |
| `keyCode` | the key's name, like `"e"`, `"space"`, `"mousebutton1"` or `"buttonA"`; `"unknown"` for the wheel, mouse movement and keys without a name |
| `state` | `begin`, `change`, `end` or `cancel` |
| `position` | where it happened, see below |
| `delta` | how much it changed, see below |

What each kind of input fills in:

| `inputType` | Fires | `position` | `delta` |
|---|---|---|---|
| `keyboard` | `inputBegan` on press, `inputEnded` on release | the pointer, in gui-scaled pixels | zero |
| `mouseButton1` to `3` | the same | the pointer | zero |
| `mouseWheel` | `inputChanged` for each scroll | the pointer, with `z` 1 scrolling up and -1 down | `z` is how far it scrolled |
| `mouseMovement` | `inputChanged` each frame the mouse moved | the pointer | the same numbers as `input.mouseDeltaX` and `mouseDeltaY` |
| `gamepad` | see [Gamepads](#gamepads) | | |

A held key does not repeat. The same object follows a key from `begin` to `end`, so you can keep
it and read `state` later. Nothing sends `cancel` yet; `inputEnded` hears it when something does.

`gameProcessed` is true when the game had it first:

- a Minecraft screen is open, the chat included,
- a loading overlay is up, or the editor is open,
- an interface took the click,
- a [bound action](#binding-actions) sank it.

Check it at the top of every handler and return early, the way the example does, or a player typing in
the chat fires your gameplay code. Input the game had first never reaches bound actions, so typing `e`
in the chat does not run the action bound to `e`.

## Keys held now

The three events tell you when something changed. These calls tell you what is held right now:

```lua
input:isKeyDown("leftshift")
input:isKeyDown("z")                   -- the key that types z
input:isMouseButtonPressed(1)          -- or "mouseButton1"
for _, key in input:getKeysPressed() do
    print(key.keyCode)
end
```

These read the keyboard itself: a key a bound action sank, or one held while the chat is open,
still reads as down. `input:down("sneak")` answers what Minecraft saw instead.

`getKeysPressed` only lists keyboard keys, as `InputObject`s in the `begin` state. `isKeyDown` also
takes mouse and gamepad names. A name that is not a key is an error.

## Key names

Letters name what the key **types on the player's layout**. On AZERTY the key a qwerty board calls W
types z, so it is `"z"` in `keyCode`, and `input:isKeyDown("z")` asks for it. Write the letter the
player sees on the key. Digits are always `"0"` to `"9"`, whatever the digit row types without shift.
Other keys that type a character are named by it, like `"é"` or `"<"`.

The rest: `f1`–`f12`, `keypad0`–`keypad9`, `keypadenter`, `space`, `enter`, `tab`, `escape`,
`backspace`, `delete`, `insert`, `home`, `end`, `pageup`, `pagedown`, `capslock`, `leftshift`,
`rightshift`, `leftcontrol`, `rightcontrol`, `leftalt`, `rightalt`, `leftsuper`, `rightsuper`, `up`,
`down`, `left`, `right`, `mousebutton1`, `mousebutton2`, `mousebutton3`, `mousewheel`,
`mousemovement`, and the [gamepad names](#gamepads). Case does not matter when you pass one in.
A key with none of these names arrives as `"unknown"`.

To show a key to the player, ask for its label:

```lua
input:keyName("z")            -- "Z"
input:keyName("leftshift")    -- "Left Shift"
input:keyName("jump")         -- whatever the player bound jump to, like "Space"
hint.text = "Press " .. input:keyName("use") .. " to open"
```

`keyName` answers in the player's layout and language. It also takes Minecraft's actions:
`forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`, `attack`, `use` and `pointer`, and
answers the key bound to them. So `input:keyName("left")` is the strafe key, while
`input:isKeyDown("left")` is the arrow key.

> [!IMPORTANT]
> For movement, never ask for a letter: WASD is ZQSD on AZERTY. Ask for the action with
> `input:down("forward")`, and label it with `input:keyName("forward")`.

<!-- demo:camera-keys -->

## Mouse behaviour

`input.mouseBehavior` says what the mouse does while the player is in the world:

```lua
input.mouseBehavior = "lockCenter"            -- captured, turning the camera: normal play
input.mouseBehavior = "default"               -- the pointer is free, like input:releaseMouse()
input.mouseBehavior = "lockCurrentPosition"   -- the pointer stays and hides where it is
input.mouseIconEnabled = false                -- hides the pointer while it is free
```

With `lockCurrentPosition` the camera does not turn and `input.mouseX` and `mouseY` stay on the
spot, but moving the mouse still fires `mouseMovement` and fills `mouseDeltaX` and `mouseDeltaY`.
That is dragging to spin something. It lets go when a screen or the editor opens, or when a script
sets another behaviour. While a screen is open it acts like `default`.

Reading `mouseBehavior` answers what the mouse is doing now, so it reads `lockCenter` during normal
play. `mouseIconEnabled` is the same switch as `window.cursorVisible`, see [Windows](window.md).

## Binding actions

`input:bindAction` runs a function when one of its keys is pressed, before Minecraft sees it:

```lua
input:bindAction("dash", function(name, state, object)
    if state ~= "begin" then return end
    local body = game.players:me() :: Character
    body:applyImpulse(body.worldCframe.lookVector * 20 + vec3(0, 4, 0))
end, "leftshift")
```

- The first argument names the binding. You unbind and replace it by that name.
- The handler is called with the name, the state and the `InputObject`, so one function can serve
  several bindings.
- Every key after the function is a key that triggers it.
- The guard on `state` is what keeps the dash from firing again when the key comes back up.

The handler hears `begin` and `end`, and `change` for the wheel, mouse movement and sticks. What it
returns decides who else gets the key:

| Returns | |
|---|---|
| nothing or `"sink"` | the key stops here: lower bindings and Minecraft never see it |
| `"pass"` | the next binding hears it, and Minecraft does when every binding passes |

The dash above returns nothing, so left shift no longer sneaks. A menu key that keeps the inventory
from opening:

```lua
local menu = hud:find("Menu") :: Frame
input:bindAction("menu", function(name, state)
    if state == "begin" then
        menu.visible = not menu.visible
    end
    return "sink"
end, "e")
```

To hear a key and still let the game have it, pass:

```lua
local jumps = 0
input:bindAction("countJumps", function(name, state)
    if state == "begin" then jumps += 1 end
    return "pass"
end, "space")
```

A key sunk when pressed is kept from Minecraft until it is released, so the game never sees half a
press.

The rest of the calls, and binding at a priority of your own:

```lua
input:bindAction("fire", onFire, "mousebutton1", "buttonR2")      -- any of the keys
input:bindActionAtPriority("cutscene", function() end, 100, "e", "space", "leftshift")
input:unbindAction("dash")
input:getBoundActions()                                           -- { "menu", "countJumps", ... }
```

- Higher priority runs first. With equal priority, the latest binding runs first. `bindAction` is
  priority 0.
- Binding a name again replaces it.
- A binding goes away when the script that made it stops.
- An action needs at least one key, and a name that is not a key is an error.
- `mousewheel` sinks the scroll, so the hotbar does not change. `mousemovement` is heard but cannot
  be sunk: the camera still turns.
- Minecraft does not read gamepads, so sinking a gamepad key only stops lower bindings.

<!-- demo:camera-bindings -->

An `InputAction` instance is still the way to name keys a place checks with `input:down`, see
[Movement](movement.md#input-actions).

## Gamepads

A gamepad reaches you two ways: through the input events, like a key, and through a state object you
read whenever you want.

```lua
input.gamepadConnected:connect(function(pad) print("gamepad", pad, "plugged in") end)
input.gamepadDisconnected:connect(function(pad) end)

input:getConnectedGamepads()          -- { 1 }
local pad = input:getGamepadState(1)  -- with no number, the first one; nil when there is none
pad.leftStick                         -- x right, y up, each from -1 to 1
pad.rightStick
pad.leftTrigger                       -- 0 to 1
pad.rightTrigger
pad.buttons.buttonA                   -- true while held
```

Gamepads are numbered from 1 by the slot they are in, so unplugging the first leaves the second as 2.
One already plugged in when the place starts fires `gamepadConnected` on the first frame.

> [!NOTE]
> After a script reload `gamepadConnected` does not fire again, so read `getConnectedGamepads()` when
> the script starts too, or a controller that was already plugged in goes unnoticed.

Only controllers the game recognises as gamepads count. There is no vibration.

| Name | |
|---|---|
| `buttonA`, `buttonB`, `buttonX`, `buttonY` | the face buttons |
| `buttonL1`, `buttonR1` | the shoulder buttons |
| `buttonL2`, `buttonR2` | the triggers |
| `buttonL3`, `buttonR3` | pressing the sticks in |
| `buttonSelect`, `buttonStart`, `buttonGuide` | back, start and the logo button |
| `dPadUp`, `dPadDown`, `dPadLeft`, `dPadRight` | the d-pad |
| `thumbstick1`, `thumbstick2` | the left and right sticks |

They arrive in input events with `inputType` `"gamepad"` and one of these as `keyCode`:

- Buttons fire `begin` and `end`.
- Triggers fire `begin` past a tenth, `change` as they move, and `end` when let go. `position.z` is
  how far it is pulled, `delta.z` how much that changed.
- Sticks only fire `change`. `position` is the stick, with `y` up, and `delta` how much it moved.
  A stick within a tenth of the middle reads zero.

The event does not say which gamepad sent it; `getGamepadState` does. `isKeyDown("buttonA")` is true
while any gamepad holds it, a trigger past a tenth or a stick past half. `buttons` in the state
holds every button and both triggers, but not the sticks.

The names work in `bindAction` and in an `InputAction`'s `keys`. A camera flown with a controller:

```lua
camera.mode = "scriptable"
local position = vec3(0, 80, 0)
local yaw = 0

game.renderStepped:connect(function(dt)
    local pad = input:getGamepadState()
    if pad == nil then return end
    yaw -= pad.rightStick.x * 2 * dt
    local facing = cframe(position) * cframe.angles(0, yaw, 0)
    local lift = pad.rightTrigger - pad.leftTrigger
    position += (facing.lookVector * pad.leftStick.y + facing.rightVector * pad.leftStick.x + vec3(0, lift, 0)) * 12 * dt
    camera.cframe = cframe(position) * cframe.angles(0, yaw, 0)
end)

input:bindAction("snap", function(name, state)
    if state == "begin" then camera:fovPunch(3, 0.2) end
end, "buttonA")
```

- The camera is read and written every frame on `renderStepped`, and everything is multiplied by `dt`,
  so the flight moves at the same speed whatever the frame rate is.
- `yaw` is kept in a variable because the stick says how fast to turn rather than where to point.
- `facing.lookVector` and `facing.rightVector` turn the left stick into movement along the direction
  the camera already faces, and the triggers move it straight up and down.
- Returning nothing from the `snap` handler sinks `buttonA`, which costs nothing here because
  Minecraft does not read gamepads anyway.
