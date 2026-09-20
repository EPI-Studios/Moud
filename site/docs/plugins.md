# Editor plugins

A plugin is a Luau file the editor runs while you edit a scene. It can add buttons to the viewport
toolbar, commands with keyboard shortcuts to a **Plugins** menu, and panels of its own. It changes
the scene the way you would by hand: every change it makes is an edit you can undo.

```lua
--!strict
-- plugins/hello.luau
local plugin = require("@moud/plugin")
plugin:command("Say hello", "Ctrl+Shift+H", function()
    print(`{plugin.name}: {#plugin.selection:get()} selected`)
end)
```

Save that file in your project's `plugins/` folder and the command shows up under **Plugins** at
once. Pressing <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>H</kbd> prints `hello: 2 selected` to the
Output panel when two things are selected.

## Where plugins live

The editor reads plugins from two folders:

| Folder | Runs in |
|---|---|
| `plugins/` in the project, next to `place.toml` | this project only. It travels with the project |
| `~/.moud/plugins/` in your home folder | every project you open |

**Plugins > Open Plugins Folder** and **Plugins > Open User Plugins Folder** open them, and make them
first if they do not exist yet.

- Every `.luau` file directly in one of the folders is one plugin. Subfolders are not read, and
  neither are `.d.luau` files.
- A plugin's name is its file name without `.luau`. `plugins/grid.luau` is the plugin `grid`.
- When both folders have a plugin with the same name, the project's runs and the other one does not.
  The Output panel says `grid is both a project and a user plugin, the project one runs`.
- Project plugins load before user plugins, and each folder loads in file name order.

## When plugins run

Plugins run while the editor is editing a scene. They do not run while the place plays, and they
never run in an exported game.

- Pressing play stops every plugin. Going back to editing runs every plugin again from the top.
- The editor looks at both folders twice a second. Saving a plugin runs that plugin again from the
  top, adding a file loads it, and deleting one unloads it. The Output panel says `loaded grid`,
  `reloaded grid` or `unloaded grid`.
- **Plugins > Reload Plugins** stops every plugin and runs them all again, which also forgets every
  module they had required.

A plugin that is unloaded or run again loses everything it made: its toolbar buttons, its commands,
its panels, its preview folder, its connections and the tasks it started. The file running again
makes them afresh. What it changed in the scene stays, because that went into the scene.

### What a plugin can reach

All plugins share one Luau runtime on your own client, separate from the place's scripts:

- `plugin` is what this page is about. Start a plugin file with
  `local plugin = require("@moud/plugin")`: that gives the type checker the Plugin types, while game
  scripts never see `plugin` in their completions. Requiring it from a game script fails with
  `@moud/plugin can only be required by a plugin script`. The bare `plugin` global also works at run
  time, but the type checker doesn't know it.
- `game.world` is the scene you are editing, the same tree the Explorer shows.
- `game.renderStepped` fires once per editor frame, and `task` runs as it does in a `LocalScript`.
  `game.stepped` never fires.
- `print` writes to the Output panel.
- `camera`, `input` and the blocks API are not there.
- Scripts inside the scene do not run in the plugin runtime.

### Errors

An error in a plugin goes to the Output panel with the plugin's name in front of it, and the other
plugins keep going. That holds for an error while the file first runs, in a button click, in a
command, in a task and in a connection.

A panel is the exception. When its draw function errors, the panel stops drawing and shows
`This panel stopped after an error, see the output. Save the plugin to try again.` until the plugin
runs again.

### Modules

`require` takes a path from the project root, the same as in a place:

```lua
local snapping = require("@plugins/lib/snapping")
```

- `@plugins/...` reads from the project's `plugins/` folder, and from `~/.moud/plugins/` when the
  project has no such file. `@plugins/lib/snapping` is `plugins/lib/snapping.luau` in the project,
  or else `~/.moud/plugins/lib/snapping.luau`. So a plugin in `~/.moud/plugins/` requires the
  modules next to it the same way.
- Put modules in a subfolder such as `lib/`, since every file directly in a plugins folder is
  loaded as a plugin of its own.
- `@client/...` and `@shared/...` read the place's own modules. `@server/...` is refused:
  `a plugin cannot require res://server/tools.luau, plugins run on your client`.
- Anything else is refused with the folders a plugin can require from:
  `res://lib/snapping.luau is not in plugins/, client/ or shared/`. A missing file under `@plugins/`
  names both places it looked: `there is no res://plugins/lib/snap.luau in plugins/ or ~/.moud/plugins/`.
- A module is loaded once and shared by every plugin that requires it.
- Saving a module runs again every plugin that required it, directly or through another module. The
  module and every module that required it are loaded afresh, and the Output panel says
  `reloaded grid` for each plugin. Plugins that never required it keep running.

New projects have a `.luaurc` with `plugins` and `moud` aliases, so the editor's type checker
follows `require("@plugins/...")` and `require("@moud/plugin")` too. A project whose `.luaurc` is
still one Moud wrote gets the aliases the next time the types are written. A `.luaurc` you changed
yourself is left alone: add `"plugins": "./plugins"` and `"moud": "./.moud"` to its `aliases`.

## Toolbar buttons

```lua
plugin:toolbar(name: string): PluginToolbar
toolbar:button(text: string, tooltip: string?, icon: string?): PluginButton
```

A toolbar is a group of buttons in the viewport toolbar, after the editor's own tools and set apart
from them by a separator. Calling `plugin:toolbar` again with the same name gives back the same
group. The name is not shown.

```lua
--!strict
local plugin = require("@moud/plugin")
local tools = plugin:toolbar("Grid")
local stamp = tools:button("Stamp", "Place blocks by clicking", "Grid")

stamp.click:connect(function()
    stamp.active = not stamp.active
end)
```

| Field | Default | |
|---|---|---|
| `text` | the first argument | shown on the button when it has no icon, and as the tooltip when `tooltip` is empty |
| `tooltip` | `""` | shown while the mouse is over the button |
| `icon` | `""` | an editor icon name. The button shows `text` instead when empty or unknown |
| `active` | false | draws the button pressed in, for a mode that is on |
| `enabled` | true | off: the button is greyed out and clicking it does nothing |
| `click` | | a signal, fired when the button is clicked |

`text` cannot be empty: `a button needs some text`. Each call to `button` adds another button, even
with the same text.

An icon is named after its file in the editor, and the case does not matter. An unknown name leaves
a warning in the Output panel: `grid: there is no editor icon called 'Grids', the button shows its text`.
The names are:

`Add`, `Remove`, `Edit`, `Duplicate`, `ActionCopy`, `ActionPaste`, `Save`, `Load`, `File`, `Folder`,
`Play`, `Pause`, `Stop`, `Redo`, `Lock`, `Unlock`, `Grid`, `Snap`, `Line`, `Rectangle`, `Bucket`,
`Eraser`, `ColorPick`, `ToolSelect`, `ToolMove`, `ToolRotate`, `ToolScale`, `ToolPivot`, `Script`,
`Shader`, `Animation`, `AnimationPlayer`, `AudioStream`, `AudioStreamPlayer3D`,
`AudioStreamMicrophone`, `AudioListener3D`, `Camera3D`, `DirectionalLight3D`, `OmniLight3D`,
`SpotLight3D`, `PointLight2D`, `WorldEnvironment`, `FogVolume`, `Weather`, `LightmapProbe`, `Mesh`,
`MeshInstance3D`, `Node2D`, `Node3D`, `Control`, `Container`, `CanvasLayer`, `CanvasGroup`,
`StaticBody3D`, `RigidBody3D`, `RigidBody2D`, `CharacterBody3D`, `CharacterBody2D`,
`CollisionShape3D`, `CollisionShape2D`, `HingeJoint3D`, `NavigationAgent3D`, `NavigationRegion3D`,
`RemoteTransform3D`, `Label3D`, `Decal`, `GPUParticles3D`, `Sprite2D`, `AnimatedSprite2D`,
`TileMap`, `TerrainConnect`, `TerrainMatchCorners`, `TerrainMatchCornersAndSides`,
`TerrainMatchSides`, `PackedScene`, `StandardMaterial3D`, `Texture2D`, `AtlasTexture`,
`GradientTexture1D`, `StyleBoxFlat`, `GraphEdit`, `GuiVisibilityHidden`, `GuiVisibilityVisible`,
`LineEdit`, `ScrollContainer`, `VBoxContainer`, `GridContainer`, `MarginContainer`,
`AspectRatioContainer`, `TextureButton`.

## Commands and shortcuts

```lua
plugin:command(name: string, handler: () -> ())
plugin:command(name: string, shortcut: string?, handler: () -> ())
```

A command is an entry in the **Plugins** menu, with its shortcut written next to it. Choosing it or
pressing the shortcut calls `handler`. The shortcut can be left out, and the types accept both
forms.

```lua
--!strict
local plugin = require("@moud/plugin")
plugin:command("Select parts", "Ctrl+Shift+P", function()
    local parts = {}
    for _, child in game.world:children() do
        if child:isA("Part") then
            table.insert(parts, child)
        end
    end
    plugin.selection:set(parts)
end)

plugin:command("Clear selection", function()
    plugin.selection:set({})
end)
```

- The name cannot be empty: `a command needs a name`.
- A second command with the same name from the same plugin replaces the first.

A shortcut is up to three modifiers and one key, joined with `+`:

| Part | Written |
|---|---|
| modifiers | `Ctrl` (or `Control`), `Shift`, `Alt`, in any order and any case |
| key | a letter `A` to `Z`, a digit `0` to `9`, or `F1` to `F12` |

`Ctrl+Shift+G`, `alt+1` and `F5` are shortcuts. The modifiers have to match exactly: `Ctrl+G` does
not fire while <kbd>Shift</kbd> is also held. Anything else, like `Ctrl+Space` or `Cmd+G`, is not a
shortcut. The command stays in the menu without one, and the Output panel says
`grid: 'Ctrl+Space' is not a shortcut, use something like Ctrl+Shift+G or F5`.

A letter is the key that types it on your keyboard, whatever the layout. On a French AZERTY
keyboard, `Ctrl+Shift+A` fires on the key marked A, where a US keyboard has Q. The editor's own
shortcuts work the same way.

Shortcuts do nothing while you type in a text field or fly the viewport camera.

The editor's own shortcuts come first. When a plugin picks one the editor already uses, such as
<kbd>Ctrl</kbd>+<kbd>D</kbd>, pressing it duplicates as before and the command gets no shortcut. It
stays in the menu, with nothing written next to it, and the Output panel says once
`grid: Ctrl+D is the editor's Duplicate shortcut, so 'Align' gets no shortcut`.

<!-- demo:plugins-shortcut -->

## Panels

```lua
plugin:panel(title: string, options: PanelOptions?): PluginPanel
panel:onDraw(draw: (ui: PanelUi) -> ())
```

A panel is a window of the editor's own, drawn by your code. It is listed under **Plugins**, where
choosing it shows or hides it, and its close button hides it too.

```lua
--!strict
local plugin = require("@moud/plugin")
local count = 3

plugin:panel("Rows", { dock = "right", width = 280, height = 160 }):onDraw(function(ui)
    ui:heading("Rows")
    count = ui:number("Count", count, 1)
    if ui:button("Print") then
        print(count)
    end
end)
```

| Option | Default | |
|---|---|---|
| `width` | 320 | pixels, at the editor's scale. At least 1 |
| `height` | 240 | pixels, at the editor's scale. At least 1 |
| `dock` | `"float"` | `"float"`, `"left"`, `"right"` or `"bottom"`: where the panel first appears |

The size and the dock only apply the first time the panel is shown. After that the panel stays where
you move it, like the editor's own panels.

| Field | |
|---|---|
| `title` | the window title. Cannot be empty: `a panel needs a title` |
| `visible` | whether the panel is shown. True when the panel is made, unless you closed it |

Two panels from one plugin may share a title. Each is its own window.

The editor remembers which panels you closed or opened, by plugin and title. A panel you closed stays
closed when its plugin runs again, after a save or **Reload Plugins**, until you open it from the
**Plugins** menu. The editor forgets this when the game closes.

### Drawing a panel

The panel calls its draw function every frame it is on screen, from the top, and draws what the
function asks for as it asks for it. Nothing is kept between frames except what your code keeps: a
widget is given the current value and returns the value it shows now, which is either the same one
or what the user just changed it to. Store it and pass it back in the next frame.

```lua
--!strict
local plugin = require("@moud/plugin")
local label = "door"
local open = false

plugin:panel("Door"):onDraw(function(ui)
    label = ui:input("Label", label)
    open = ui:checkbox("Open", open)
end)
```

Calling `onDraw` again replaces the draw function. `ui` only works while its panel draws: keeping it
and calling it later, from a command say, fails with `the panel ui can only be used while its panel
draws`.

### Widgets

| Call | Returns | |
|---|---|---|
| `ui:heading(text)` | | a section title |
| `ui:text(text)` | | text, wrapped at the panel's edge |
| `ui:muted(text)` | | text in the dimmer colour |
| `ui:separator()` | | a line across the panel |
| `ui:sameLine()` | | puts the next widget on the same line as the last one |
| `ui:button(label)` | `boolean` | true in the frame the button is clicked |
| `ui:input(label, value)` | `string` | a one-line text field |
| `ui:number(label, value, step?)` | `number` | a number field. `step` is how much one drag step changes it, 0.1 when left out |
| `ui:slider(label, value, min, max)` | `number` | a number between `min` and `max`, both included |
| `ui:checkbox(label, value)` | `boolean` | a switch |
| `ui:color(label, value)` | `Color` | a colour swatch that opens a picker, alpha included |
| `ui:choice(label, value, options)` | `string` | a drop-down list of `options` |

`text`, `muted` and `heading` take any value and show it the way `print` would.

- `input` keeps up to 1023 characters and returns the text as it is being typed, keystroke by
  keystroke. While the field has the cursor, a different `value` passed in does not overwrite what
  is being typed.
- `number` and `slider` show single precision. A value the user did not touch comes back exactly as
  it went in. One they changed comes back as the digits the field shows: dragging `1` up by `0.1`
  gives `1.1`, not `1.100000023841858`.
- `slider` fails with `a slider needs its max above its min` when `max` is not above `min`.
- `choice` fails with `a choice needs at least one option` for an empty list. A `value` that is not
  one of the options is still shown.

Every widget but `button` draws its label on the left and the control on the right. The label is
also what tells two widgets apart. To give two widgets the same label, add `##` and an id after it:
only what comes before `##` is shown.

```lua
--!strict
local plugin = require("@moud/plugin")
local width, depth = 1, 1

plugin:panel("Box"):onDraw(function(ui)
    width = ui:number("Size##width", width)
    depth = ui:number("Size##depth", depth)
    if ui:button("Reset##box") then
        width, depth = 1, 1
    end
end)
```

Both fields read `Size`, and each keeps its own value while it is being dragged or typed in.

## The selection

```lua
plugin.selection:get(): { Instance }
plugin.selection:set(instances: { Instance })
plugin.selection.changed: AnySignal
```

`get` returns what is selected in the editor. `set` replaces the selection, and `set({})` clears
it.

`changed` fires once per frame in which the selection is different from the frame before, whoever
changed it. A plugin's own `set` fires it in the next frame.

`set` refuses:

| Given | Error |
|---|---|
| something other than an instance in the list | `selection:set expects a list of instances` |
| a destroyed instance | `cannot select brick, it has been destroyed` |
| `game.world`, the preview folder, or anything else outside the scene | `cannot select brick, it is not part of the scene` |

A new instance the plugin added in the same call can be selected: its copy is selected when it
lands. See [New instances](#new-instances). A new instance moved out of the scene again before the
call ends, into the preview say, is not going to land, and `set` refuses it with
`cannot select marker, it is not part of the scene`.

## Changing the scene

A plugin changes the scene with the same Luau as any script. The editor sees each change to an
instance in the scene, applies it, sends it to the server, and keeps it for undo. The new value can be
read back straight away.

```lua
--!strict
local plugin = require("@moud/plugin")
for _, chosen in plugin.selection:get() do
    if chosen:isA("Part") then
        local part = chosen :: Part
        part.anchored = true
        part.position += vec3(0, 1, 0)
    end
end
```

An instance is in the scene when the editor lets you select it. `game.world`, the plugin's preview
folder and characters are not. These become edits:

| In the plugin | In the editor |
|---|---|
| `part.color = ...`, and any other property | a property change |
| `position`, `rotation`, `worldCframe`, `pivotTo` | a change of `cframe` |
| `part.name = ...` | a rename |
| `part.parent = other`, with `other` in the scene or `game.world` | a move |
| `part:destroy()`, `folder:clearAllChildren()` | a delete |
| `addTag`, `removeTag` | a tag change. Adding a tag it already has, or removing one it does not have, does nothing |
| `setAttribute` | an attribute change |
| `add`, `addAll` or `clone` into the scene, or `parent =` a new instance into it | an addition. See [New instances](#new-instances) |

Instances outside the scene, such as the preview folder and whatever is in it, are written directly
and nothing is recorded.

### New instances

A new instance in the scene is first made like any other: it is returned, its properties can be set,
and children can be added to it. When the call that made it ends, the editor saves it with
everything under it, destroys it, and asks the server to add that copy to the scene. The copy shows
up in the scene a moment later, when the server answers.

> [!IMPORTANT]
> A new instance is only good until the end of the call that made it. After that the variable holds
> a destroyed instance, and the one in the scene is a different instance that arrives a moment later.
> To reach it again, find it in the scene when you need it, or select it in the same call.

```lua
--!strict
local plugin = require("@moud/plugin")
plugin:command("Add marker", function()
    plugin:recording("Add marker", function()
        local marker = game.world:add("Part", {
            name = "marker",
            anchored = true,
            size = vec3(1, 1, 1),
        }) :: Part
        marker.color = color(1, 0.8, 0.2)
        plugin.selection:set({ marker })
    end)
end)
```

The marker is selected once it lands. `set` can mix new instances with ones already in the scene: the
ones in the scene are selected at once and each new copy joins them as it lands, so in the end
everything in the list is selected.

A call is one of these, and changes made in it are kept together:

- the plugin file running from the top
- a toolbar button's `click`
- a command
- one panel drawing one frame
- a viewport click reaching `plugin.viewportClicked`
- the selection changing, reaching `plugin.selection.changed`
- one editor frame of `game.renderStepped` connections and `task` callbacks

### What is refused

| Doing | Error |
|---|---|
| pointing an instance property of a scene instance at something outside the scene | `<name>.<property> can only point at something in the scene, and <target> is not in it yet` |
| moving a scene instance out of the scene, into the preview or into a new instance | `<name> can only move to somewhere in the scene, and <parent> is not` |
| renaming a scene instance to `""` | `a name can not be empty` |
| an empty tag | `a tag can not be empty` |
| changing, renaming or destroying `game.world` itself | `that instance is not part of the scene` |
| writing a read-only property | `<Class>.<property> is read-only` |
| a tween on a scene instance | `a plugin cannot tween brick, it is part of the scene. Set its properties instead` |
| `setOwner` on a scene instance | `a plugin cannot set the owner of brick, it is part of the scene` |

Setting an instance property to `nil` is allowed.

Only the calls in the first table are edits. A tween would write the instance every frame without the
editor, so it is refused on anything in the scene. Tweens on the preview folder and what is in it
work as in any script. To animate a scene instance and keep the result, set the property yourself.

## Undo

Everything a plugin changes in the scene during one call is one entry in the editor's undo history,
named after the plugin. <kbd>Ctrl</kbd>+<kbd>Z</kbd> takes the whole call back at once. Two plugins
never share an entry: when `game.renderStepped` connections or tasks from two plugins change the scene
in the same frame, each plugin's changes are an entry of their own, named after it.

```lua
plugin:recording(name: string, changes: () -> ())
```

`recording` gives the changes a name of your own, the one shown in **Edit > Undo**:

1. Whatever the call had already changed is closed as its own entry.
2. `changes` runs.
3. Everything it changed becomes one entry called `name`.

A `recording` inside another `recording` joins the outer one, under the outer name.

An error inside `changes` reaches the code that called `recording`, the same as calling `changes`
directly: it stops there, unless it is caught with `pcall`. The changes made before the error are not
undone. They stay in the scene as one entry called `name`.

```lua
--!strict
local plugin = require("@moud/plugin")
local ok, why = pcall(function()
    plugin:recording("Rename all", function()
        for _, chosen in plugin.selection:get() do
            chosen.name = ""
        end
    end)
end)
if not ok then
    print(`stopped: {why}`)
end
```

A panel's draw function is no exception: a `recording` that errors while the panel draws stops the
panel like any other error in its draw function.

While the mouse button is held, or while a field in a panel is being used, entries that set the same
properties on the same instances merge into one. Dragging a slider that sets a part's `transparency`
every frame is one undo step, not one per frame.

<!-- demo:plugins-undo -->


## The mouse and the viewport

```lua
plugin:mouse(): PluginMouse
plugin.viewportClicked: PluginMouseSignal
plugin:activate(on: boolean)
plugin.active: boolean
```

`plugin:mouse()` casts a ray from the viewport camera through the mouse, whether or not the mouse is
over the viewport, and returns a table:

| Field | |
|---|---|
| `origin` | where the ray starts, at the camera |
| `direction` | which way it goes |
| `hit` | the point it hit, or nil |
| `normal` | the face it hit, pointing out of it, or nil |
| `target` | the scene instance it hit, or nil for a block or nothing |
| `over` | whether the mouse is over the viewport |

The ray hits scene parts and blocks up to 256 metres away, whichever is nearer. It does not hit the
preview folder, `game.world` or anything else outside the scene. While the viewport has no size, as
before it is first drawn, `origin` and `direction` are both `vec3(0, 0, 0)` and `over` is false.

`plugin.viewportClicked` fires with the same table when the left mouse button goes down in the
viewport. It does not fire while a gizmo is being dragged, while the camera is looking around, or
with <kbd>Alt</kbd> held. It fires for every plugin, active or not.

`plugin:activate(true)` tells the editor this plugin wants viewport clicks for itself. While any
plugin is active, a click in the viewport picks nothing and starts no box selection. `activate(false)`
gives the clicks back, and so does the plugin being unloaded. `plugin.active` reads the current state.

The table's type is `PluginMouse`, and the options `plugin:panel` takes are `PanelOptions`. Both can
be written in your own code:

```lua
--!strict
local plugin = require("@moud/plugin")
local function describe(mouse: PluginMouse): string
    return if mouse.target then mouse.target.name else "nothing"
end

plugin.viewportClicked:connect(function(mouse)
    print(describe(mouse))
end)
```

## The preview folder

```lua
plugin.preview: Instance
```

`plugin.preview` is a folder in the world that belongs to the plugin and not to the scene: a ghost
of what a tool will place, a guide line, a marker. It is called `grid preview` for a plugin named
`grid`.

- It is made the first time it is read, and made again if it was destroyed.
- What is in it is not part of the scene. It is not saved, not sent to the server, not in undo, not
  selectable and not hit by `plugin:mouse()`.
- It is destroyed when the plugin is unloaded or runs again.

A part in the preview is written directly, so it is cheap to move every frame.

## Settings

```lua
plugin:setting(key: string): any
plugin:setSetting(key: string, value: any)
```

Settings are values a plugin keeps between editor sessions. They are kept per plugin, in
`~/.moud/plugin-settings.json`, and shared by every project: a `grid` plugin in two projects reads
the same settings. A plugin in `~/.moud/plugins/` keeps its own, under `~/grid` in the file, so a
project plugin and a user plugin that share a name never read each other's settings.

```lua
--!strict
local plugin = require("@moud/plugin")
local saved = plugin:setting("step")
local step: number = if type(saved) == "number" then saved else 1

plugin:panel("Step"):onDraw(function(ui)
    local picked = ui:number("Step", step, 0.25)
    if picked ~= step then
        step = picked
        plugin:setSetting("step", step)
    end
end)
```

- `setting` returns nil for a key that was never set.
- `setSetting(key, nil)` removes the key.
- A value is a string, a number, a boolean, or a table of those. A table's keys are all strings or it
  is a list. Anything else fails: `settings keep strings, numbers, booleans and tables of them, not a
  Vector3`, or `settings keep tables with string keys`.
- `setting` reads back what `setSetting` set at once. The file is written at most once a second, and
  again when plugins stop, a plugin is unloaded or the game closes, so calling `setSetting` every
  frame is harmless.

## A plugin's name

`plugin.name` is the plugin's name, its file name without `.luau`. It, `plugin.active` and
`plugin.preview` can be read, not written: assigning one fails with `Plugin.name cannot be assigned`.
The types mark them `read`, which the type checker enforces when its new solver is on.

## A grid tool

`plugins/grid.luau` snaps the selected parts to a grid, and stamps new blocks onto the grid where you
click. It has a toolbar button for the stamp mode, a command with a shortcut for aligning, and a
panel that sets the grid step and keeps it between sessions.

```lua
--!strict
local plugin = require("@moud/plugin")

local saved = plugin:setting("step")
local step: number = if type(saved) == "number" then saved else 1
local stamping = false

local function snap(at: Vector3): Vector3
    return vec3(
        math.round(at.x / step) * step,
        math.round(at.y / step) * step,
        math.round(at.z / step) * step
    )
end

local function align()
    plugin:recording("Align to grid", function()
        for _, chosen in plugin.selection:get() do
            if chosen:isA("Part") then
                local part = chosen :: Part
                part.position = snap(part.position)
            end
        end
    end)
end

local ghost = plugin.preview:add("Part", {
    name = "ghost",
    anchored = true,
    collides = false,
    transparency = 0.6,
    visible = false,
}) :: Part

local function stampAt(mouse: PluginMouse): Vector3?
    local hit, normal = mouse.hit, mouse.normal
    if not hit or not normal then
        return nil
    end
    return snap(hit + normal * (step / 2))
end

local stampButton = plugin:toolbar("Grid"):button("Stamp", "Click in the viewport to place a block", "Grid")

local function setStamping(on: boolean)
    stamping = on
    stampButton.active = on
    plugin:activate(on)
    ghost.visible = false
end

stampButton.click:connect(function()
    setStamping(not stamping)
end)

plugin:command("Align to grid", "Ctrl+Shift+G", align)
plugin:command("Stop stamping", function()
    setStamping(false)
end)

game.renderStepped:connect(function()
    if not stamping then
        return
    end
    local mouse = plugin:mouse()
    local at = if mouse.over then stampAt(mouse) else nil
    ghost.visible = at ~= nil
    if at then
        ghost.size = vec3(step, step, step)
        ghost.position = at
    end
end)

plugin.viewportClicked:connect(function(mouse)
    if not stamping then
        return
    end
    local at = stampAt(mouse)
    if not at then
        return
    end
    plugin:recording("Stamp block", function()
        game.world:add("Part", {
            name = "block",
            anchored = true,
            size = vec3(step, step, step),
            position = at,
        })
    end)
end)

plugin:panel("Grid", { dock = "right", height = 160 }):onDraw(function(ui)
    local picked = ui:number("Step", step, 0.25)
    if picked ~= step and picked > 0 then
        step = picked
        plugin:setSetting("step", step)
    end
    ui:muted(`{#plugin.selection:get()} selected`)
    if ui:button("Align selection") then
        align()
    end
    ui:sameLine()
    if ui:button(if stamping then "Stop stamping" else "Stamp") then
        setStamping(not stamping)
    end
end)
```

- `ghost` lives in the preview, so moving it every frame records nothing and the mouse ray passes
  through it to the scene behind.
- The ghost sits half a step out of the face under the mouse, so a block stamped on top of another
  lands on it rather than inside it.
- `plugin:activate(on)` keeps a stamping click from also selecting what is under the mouse.
- Each stamp is its own undo entry called `Stamp block`, and each align is one entry called
  `Align to grid` however many parts moved.
- The new block is not kept in a variable: the one `add` returns is gone by the end of the click.

<!-- demo:plugins-grid -->
