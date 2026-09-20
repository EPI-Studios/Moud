# Building in the editor

The editor is where you lay out a scene by hand: parts, lights, zones, cameras and the joints between
them. What you build is saved into the scene's `.scene` file and loaded like anything a script adds.
See [Places, modules, scenes and scripts](place.md#scenes) for the files, and
[Physics](physics.md) for what each joint does once the place plays.

This page covers joining parts. Each other editor feature is described on the page of the thing it
builds.

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
