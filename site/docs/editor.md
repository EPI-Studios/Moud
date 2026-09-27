# Building in the editor

The editor is where you lay out a scene by hand: parts, lights, zones, cameras and the joints between
them. What you build is saved into the scene's `.scene` file and loaded like anything a script adds.
See [Places, modules, scenes and scripts](place.md#scenes) for the files, and
[Physics](physics.md) for what each joint does once the place plays.

This page covers undo, copying, moving a selection, joining parts and choosing the code editor
scripts open in. Each other editor feature is described on the page of the thing it builds.

## Undo, copy and paste

Every change you make to the scene is one step in the history: a property typed or dragged, a
gizmo drag, a rename, a drop in the Explorer, a paste, a group, a delete, an animation key.
<kbd>Ctrl+Z</kbd> undoes it and <kbd>Ctrl+Y</kbd> or <kbd>Ctrl+Shift+Z</kbd> does it again. The
history belongs to the scene that is open: opening another scene, or restoring a backup, starts a
new one. Save and Save As keep it.

Things point at each other through reference properties, such as a weld's `part0` and `part1` or a
model's `primaryPart`. The editor keeps those links when it puts things back:

- Undoing a delete points the restored things at what they pointed at, and points everything that
  pointed at them back at them.
- Group and Ungroup keep every link into and out of what they move.
- A duplicate, or a copy pasted in the same scene, points at the same things outside the copy as the
  original. Links inside the copy point inside the copy.

**Copy** puts the selection on the clipboard as scene text, so it can be pasted into another scene
or after a restart. Links to things outside the copy are only kept while the scene you copied from
is open. **Paste** (<kbd>Ctrl+V</kbd>) puts the copy next to the selected object, in its parent, where
the original was. **Paste Into** (<kbd>Ctrl+Shift+V</kbd>) puts it inside the selected object. The
pasted things are selected.

Selecting something in the viewport opens its parents in the Explorer and scrolls to it.
<kbd>F</kbd> frames the selection from anywhere in the editor except a text field.

### Playing and coming back

Playing (<kbd>F6</kbd>) runs the place from what is in the editor, saved or not. Stopping puts the
scene back as it was before you played, with the same selection and the same history, and nothing a
script made is left behind. If the scene file changed on disk while you played and the scene had no
unsaved changes, the file is loaded instead and the history starts again.

### When the scene file changes on disk

The editor checks the open scene file every second. If another program changed it and the scene has
no unsaved changes, it is loaded again. If it has unsaved changes, the status bar says so and
nothing is loaded. Saving then replaces the file, and the version on disk is kept in
`.moud/backups`, which **File > Scene Backups...** lists.

## Moving several things at once

Select several things with <kbd>Ctrl</kbd> or <kbd>Shift</kbd> click, or drag a box over them, and the
move, rotate and scale gizmos work on all of them together. A script doing what the rotate gizmo does
to a selection would look like this:

```lua
--!strict
-- server
local world = game.world
local picked = { world:find("crate") :: Part, world:find("barrel") :: Part, world:find("lamp post") :: Part }

-- the middle of the box around all three is where they turn about
local low: Vector3? = nil
local high: Vector3? = nil
for _, part in picked do
    local at = part.worldCframe.position
    local lo, hi = at - part.size / 2, at + part.size / 2
    low = if low then vec3(math.min(low.x, lo.x), math.min(low.y, lo.y), math.min(low.z, lo.z)) else lo
    high = if high then vec3(math.max(high.x, hi.x), math.max(high.y, hi.y), math.max(high.z, hi.z)) else hi
end
local pivot = ((low :: Vector3) + (high :: Vector3)) / 2

local turn = cframe.angles(0, math.rad(45), 0)
for _, part in picked do
    part.worldCframe = cframe(pivot.x, pivot.y, pivot.z) * turn * cframe(-pivot.x, -pivot.y, -pivot.z) * part.worldCframe
end
```

The gizmo sits at the selection's pivot. The **pivot** button in the viewport toolbar, next to the
World/Local button, says where that is:

- **Centre.** The middle of the box around everything selected, the parts' corners included. This is
  the default.
- **Active.** The origin of the object selected last, the one the Properties panel shows.
- **Each.** Rotate and scale work about each object's own origin. The gizmo is drawn at the centre.

An object's origin is the point its `cframe` sits on, which is its `pivot` when it has one. With one
thing selected, the gizmo is always at its origin.

- **Move** moves everything by the same amount. With snapping on, the amount snaps to the grid step,
  so things that sat on the grid stay on it.
- **Rotate** turns the selection about the pivot. Each object's position swings round the pivot and
  its own rotation turns by the same angle. With **Each**, every object turns in place. The angle
  snaps to the angle step.
- **Scale** stretches the gaps between the objects and the pivot, and the size of each part and
  zone, along the gizmo's axes. On **World** the axes are the world's; on **Local** and **Parent**
  they are those of the active object or its parent. A part turned against those axes grows along its
  own sides as closely as a box can. The factor snaps to steps of 0.1. With one part selected, scale
  shows the face handles instead.

Parts, models, lights, cameras, zones, attachments and anything else with a `cframe` can be moved
this way. What is inside a moved object goes with it. Scaling also spreads out and resizes what is
inside, so a model grows as one.

- A locked part in the selection stays where it is. If the only things selected are locked, no gizmo
  is drawn.
- Something selected together with one of its parents moves once, with the parent.
- One drag is one step in the history, labelled **Move**, **Rotate** or **Scale**, however many things
  it moved. One undo puts them all back.
- The change goes to the server the same way a change typed in the Properties panel does, so everyone
  editing the place sees it.
- Holding <kbd>Shift</kbd> as the drag starts copies the selection first and leaves the copies behind.

## Joining parts

The **constraint tool** joins two parts by clicking them. Open it with the hinge button in the
viewport toolbar, with <kbd>J</kbd> while the viewport has the mouse, or with
**Edit > Constraint Tool**.

While the tool is on, a button next to it names the kind of joint it makes. Click it to pick another:

| Kind | Makes | What the two clicks mean |
|---|---|---|
| `WeldConstraint` | one weld | the two parts. They stay together as they are |
| `HingeConstraint` | two attachments and a hinge | the second part turns about the first point, on the axis sticking out of the face you clicked |
| `PrismaticConstraint` | two attachments and a slider | the second part slides along the axis sticking out of the first face |
| `BallSocketConstraint` | two attachments and a ball socket | the second part turns any way about the first point |
| `RopeConstraint` | two attachments and a rope | the two points may get no further apart than they are now |
| `SpringConstraint` | two attachments and a spring | the two points are pulled or pushed back to the distance they are at now |
| `RodConstraint` | two attachments and a rod | the two points stay exactly as far apart as they are now |

Picking another kind drops a first click you had made.

### The two clicks

1. Click the first part. For a weld the part is all that counts. For every other kind, where you click
   on it is the first point.
2. Click the second part. The joint is made, and the tool is ready for the next pair.

A line follows the mouse from the first point, and the text by the cursor says what the next click
does. The part under the mouse is highlighted, and so is the point you would click, with a short line
out of the face it is on.

- Clicking the first part again does nothing. The point turns red and the text asks for a different
  part.
- <kbd>Escape</kbd> drops the first click. Pressed again, with nothing clicked, it leaves the tool for
  Move.
- Clicking in the constraint tool does not change the selection. A click with <kbd>Alt</kbd> held, or
  while you are turning the camera, is ignored.
- Locked parts cannot be clicked, and neither can parts a script made, since they are not part of the
  scene.
- Nothing stops you joining an anchored part. As in a script, the joint pins that end to the world,
  and a joint between two anchored parts does nothing while the place plays. See
  [the rules every constraint follows](physics.md#constraints).

### Where the points go

Each click puts an `Attachment` inside the part you clicked, where you clicked on its surface. The
constraint goes inside the first part, next to its attachment. The attachments are named
`Attachment`, `Attachment2` and so on, and the constraint after its class, so nothing already in the
part is renamed or replaced.

An attachment's X axis, its `rightVector`, points straight out of the face you clicked. Its Y axis
follows the part's own up, or the part's back when you clicked the top or bottom face. The editor
draws the X axis long and the Y axis short and faint.

<!-- demo:editor-attachment -->

How the second point is turned depends on the kind:

- **Hinge, slider and ball socket.** The second attachment takes the first one's rotation, so both
  share the axis. Its position is where you clicked the second part. When the place plays, the joint
  pulls that point onto the first one, so click the second part where the two should meet.
- **Rope, spring and rod.** Each attachment faces out of its own face. The length is the distance
  between the two points when you clicked: `length` for a rope and a rod, `freeLength` for a spring.
  A rope is made just taut, a rod keeps that exact gap, and a spring rests at it.

With the **Snap** button on, the point jumps to the grid step across the face you are on, and stays
inside it. Holding <kbd>Ctrl</kbd> turns snapping the other way, as it does when you move a part. A
slanted or curved face is not snapped. When the mouse is over a part that casts do not hit, the point is the
middle of the part, with its X axis toward the camera.

A new constraint is selected, so the Properties panel shows it at once. Set a hinge's motor or a
rope's `thickness` there. Every property is described in [Physics](physics.md#constraints).

### Welding the selection

**Ctrl+W**, or **Edit > Weld Selected**, welds the selected parts together without the tool:

- The part selected first is the one the others are welded to. Each other part gets one
  `WeldConstraint`, inside the first part.
- A pair that already has a weld between them is skipped. With every pair welded, nothing is made and
  the editor says so.
- Only parts count. A model or a folder in the selection is not looked into, and fewer than two parts
  is refused.
- Locked parts are welded too; locking only stops clicks in the viewport.
- The selection stays as it was.

To weld one part to several, click it first and add the rest with <kbd>Ctrl</kbd> or <kbd>Shift</kbd> click. A box
selection picks up parts in no order you control.

<!-- demo:editor-weld -->

### Undo

Each joint is one step in the history, labelled with what it made: **Add HingeConstraint**, **Weld**,
**Weld 3**. Undo removes the constraint and both its attachments together. Redo puts all three back
and points the constraint at the attachments again.

### Seeing constraints

Joints are drawn in pink over the viewport:

- a weld as a line between the middles of its two parts, with a circle at each end,
- any other constraint as a line between its two attachments, with a circle at each end, and hinges
  and sliders with their axis through attachment0,
- every attachment inside a part as its two axes.

A selected joint or attachment is drawn brighter.

**Helpers** (<kbd>H</kbd>) shows these along with zones, lights, sounds, cameras and spawns. The
**Constraints** toggle beside it shows only the joints and attachments, for when the other helpers are
in the way. They always show while the constraint tool is on. Neither toggle is kept when you close
the editor.

This drawing is for editing. To see a rope, spring or rod while the place plays, turn on its
`visible`; see [Physics](physics.md#constraints).

### The same joints from a script

The tool writes nothing a script cannot. These make the same kinds of joint:

```lua
--!strict
-- server
local world = game.world
local cart = world:find("cart") :: Part
local wheel = world:find("wheel") :: Part
local seat = world:find("seat") :: Part
local beam = world:find("beam") :: Part
local lamp = world:find("lamp") :: Part

-- Weld, or Ctrl+W: one weld inside the first part
cart:add("WeldConstraint", { part0 = cart, part1 = seat })

-- Hinge: the cart's right face points along +X, so the point needs no turn,
-- and the wheel's point takes the same rotation at the same spot
local a0 = cart:add("Attachment", { cframe = cframe(1, -0.2, 1.1) }) :: Attachment
local a1 = wheel:add("Attachment", { cframe = wheel.worldCframe:inverse() * a0.worldCframe }) :: Attachment
cart:add("HingeConstraint", { attachment0 = a0, attachment1 = a1 })

-- Rope: each point faces out of its own face, and the length is the gap between them
local under = beam:add("Attachment", { cframe = cframe(0, -0.25, 0) * cframe.angles(0, 0, -math.pi / 2) }) :: Attachment
local over = lamp:add("Attachment", { cframe = cframe(0, 0.25, 0) * cframe.angles(0, 0, math.pi / 2) }) :: Attachment
beam:add("RopeConstraint", {
    attachment0 = under,
    attachment1 = over,
    length = under.worldCframe.position:distance(over.worldCframe.position),
})
```

- `wheel.worldCframe:inverse() * a0.worldCframe` is the first point's place and rotation seen from
  the wheel, which is what the tool gives a hinge's second point when both clicks land on the same
  spot.
- The two rope points are turned a quarter turn about Z, one down out of the beam's bottom and one up
  out of the lamp's top.

See [Welds](physics.md#welds) and [Constraints](physics.md#constraints) for every property these
take.

## Choosing the code editor

Double clicking a script, **Open in code editor**, and the file links in Output and Export open the
script in a code editor at the line that matters. **File > Code Editor...** picks which one. The
choice belongs to your computer, not the place: it is kept in `~/.moud/preferences.json` and every
place you open uses it.

| Choice | What runs |
| --- | --- |
| Auto | the first of Zed, VS Code, VSCodium, Cursor and Sublime Text that is installed, then Notepad on Windows, the default text editor on macOS, or `xdg-open` on Linux |
| Zed | `zed <place> <file>:<line>` |
| VS Code, VSCodium, Cursor | `code <place> --goto <file>:<line>`, with `codium` or `cursor` |
| Sublime Text | `subl <place> <file>:<line>` |
| IntelliJ IDEA, WebStorm | `idea <place> --line <line> <file>`, with `webstorm` |
| System default | `xdg-open <file>` on Linux, `open -t <file>` on macOS, `start "" <file>` on Windows, with no line |
| Custom command | your own command |

The dialog shows the command it would run for `place.toml`, and **Test** runs it. When the editor you
chose is not installed, Output says so and names the program it looked for.

Each editor is looked for on your `PATH` first, then where its installer puts it: `~/.local/bin`,
`/snap/bin` and Flatpak on Linux, the app bundles in `/Applications` and Homebrew on macOS, and
`%LOCALAPPDATA%\Programs` and `Program Files` on Windows. JetBrains IDEs are found through the
launcher scripts Toolbox makes. If yours lives somewhere else, put its folder on your `PATH` or use a
custom command with the full path.

A custom command is split into words the way a shell would, with double or single quotes keeping
spaces together, but no shell runs it, so pipes and `~` mean nothing. These are filled in:

- `{file}`, the full path of the script,
- `{line}`, the line, 1 when there is none,
- `{folder}`, the place's root folder.

With no `{file}` in it, the path goes on the end.

```text
"C:\Program Files\Notepad++\notepad++.exe" -n{line} "{file}"
kitty -e nvim +{line} {file}
emacsclient -n +{line} {file}
```
