# Math

Moud has three value types for maths, and three tables of functions that work on them. `vec3` builds
a `Vector3`, which is a point or a direction in the world. `cframe` builds a frame, a position and a
rotation held together, which is what a part's `cframe` property takes. `quat` builds a rotation on
its own, with no position in it. The `math`, `angle` and `random` tables hold functions that take
plain numbers and vectors.

Everything is metres and seconds, and every angle is in radians.

## Vectors

A `Vector3` is three numbers: x and z run along the ground, y is height. The same type carries a
position, a size and a direction, so `vec3(0, 66, 0)` is a spot 66 metres up, `vec3(40, 1, 40)` is a
wide flat slab, and `vec3(0, 1, 0)` points at the sky.

```lua
local v = vec3(3, 4, 0)
v.x  v.y  v.z  v.magnitude  v.unit
v + w  v - w  v * 2  -v
v:distance(w)  v:distanceSq(w)  v:dot(w)  v:cross(w)
v:lerp(w, 0.5)  v:angleTo(w)          -- radians
v:flat()                              -- y set to 0
v:clampMagnitude(10)  v:abs()  v:floor()
```

- **`magnitude`** is how long the vector is, in metres. On the difference between two positions, that
  is the distance between them.
- **`unit`** is the same direction with a length of 1. Use it when you want a direction and the length
  is in your way, such as pushing something at a speed you choose.
- Adding and subtracting work component by component, and multiplying by a number scales all three.
  `-v` points the other way.
- **`distance`** and **`distanceSq`** measure between two positions. `distanceSq` skips the square
  root, so compare it against a squared range when you are checking many pairs each tick.
- **`dot`** tells you how much two directions agree: 1 when they point the same way, 0 when they are
  at right angles, -1 when they are opposite. **`cross`** gives a vector at right angles to both.
- **`lerp`** walks from one vector to another, `0` giving the first and `1` the second, so
  `v:lerp(w, 0.5)` is the midpoint. **`angleTo`** is the angle between two directions, in radians.
- **`flat`** copies the vector with y set to 0, which is how you measure a distance along the ground
  and ignore how far apart two things are vertically.
- **`clampMagnitude`** hands back a vector no longer than the length you give. Shorter vectors come
  back unchanged, so it caps a speed without changing its direction.
- **`abs`** makes each of the three numbers positive, and **`floor`** rounds each one down.

<!-- demo:math-vectors -->

## Frames

A frame holds a position and a rotation in one value. Parts, cameras and models all take one, so
writing an instance's `cframe` moves it and turns it in a single write.

```lua
local f = cframe(0, 65, 0)

cframe(position)                    -- at a point, unrotated
cframe(from, lookAtPoint)           -- at a point, turned to face another point
cframe.angles(pitch, yaw, roll)     -- a rotation at the origin, in radians
cframe.lookAt(from, to)             -- at `from`, facing `to`
cframe.identity                     -- the origin, unrotated
```

```lua
f.position  f.rotation  f.lookVector  f.rightVector  f.upVector
f * g
f:inverse()  f:lerp(g, t)
f:toObjectSpace(g)  f:toWorldSpace(g)
f:pointToObjectSpace(p)  f:pointToWorldSpace(p)
f:vectorToObjectSpace(v)  f:vectorToWorldSpace(v)
f:lookAt(target)                    -- same position, turned to face a point
```

- **`position`** is where the frame is and **`rotation`** is how it is turned, with the position taken
  out.
- **`lookVector`**, **`rightVector`** and **`upVector`** are the three directions the frame faces:
  forward, to its right and out of its top. They are what you use to move something the way it is
  pointing rather than the way the world is.
- **`f * g`** applies `g` inside `f`, so the second frame is read in the first one's axes. That is why
  `part.cframe = part.cframe * cframe.angles(0, dt, 0)` spins a part around its own up axis wherever
  it stands, while `cframe.angles(0, dt, 0) * part.cframe` would swing it around the world's.
- **`inverse`** gives the frame that undoes this one. **`lerp`** blends between two frames, moving and
  turning at once.
- **`toObjectSpace`** and **`toWorldSpace`** convert a whole frame in and out of this one's axes.
  `pointToObjectSpace` and `pointToWorldSpace` do the same for a position, and
  `vectorToObjectSpace` and `vectorToWorldSpace` for a direction. A direction carries no position, so
  the vector pair only turns what you give it and never shifts it.
- **`f:lookAt(target)`** keeps the frame where it is and turns it to face a point. Use it to aim a
  turret or a camera without working out the angles yourself.

<!-- demo:math-frames -->

## Rotations

A quaternion is a rotation with no position attached. Reach for one when you want to blend between
two rotations, or to store which way something is facing without saying where it is.

```lua
quat.identity
quat.axisAngle(vec3(0, 1, 0), math.pi / 2)
quat.euler(pitch, yaw, roll)
quat.lookAt(forward, up)
quat.fromTo(vec3(1, 0, 0), vec3(0, 0, -1))    -- the shortest turn between two directions
q:slerp(r, t)  q:inverse()  q:rotate(v)  q:mul(r)
```

- **`quat.identity`** is no rotation at all.
- **`quat.axisAngle`** turns by an angle around an axis you give, so the example above is a quarter
  turn around the up axis.
- **`quat.euler`** builds one from pitch, yaw and roll. **`quat.lookAt`** builds one from a forward
  direction and an up direction. **`quat.fromTo`** gives the shortest turn that takes one direction
  onto another.
- **`slerp`** blends two rotations, `0` giving the first and `1` the second, and keeps the speed of
  the turn even along the way.
- **`inverse`** undoes the rotation, **`rotate`** turns a vector by it, and **`mul`** puts two
  rotations one after the other.

> [!NOTE]
> Angles are radians everywhere in Moud, in `cframe.angles`, in `quat.euler` and in the `angle` table
> below. `math.pi / 2` is a quarter turn and `math.pi * 2` is a full one.

## Numbers

These sit alongside Luau's own `math` functions, in the same table.

```lua
math.remap(5, 0, 10, 0, 100)          -- 50
math.lerp(a, b, t)
math.inverseLerp(a, b, value)
math.approach(value, target, step)
local velocity
x, velocity = math.smoothDamp(x, target, velocity, 0.3, dt)
math.noise(x, y, z)                   -- luau's perlin noise
math.fbm(x, y, z, 4)                  -- layered noise: octaves, lacunarity, gain
math.bezier({ a, b, c }, t)           -- numbers or vectors
math.catmullRom({ a, b, c, d }, t)    -- passes through every point
```

- **`math.remap`** moves a number from one range into another. The example reads 5 out of 0 to 10 and
  writes it back into 0 to 100, which gives 50. It is how you turn health into the width of a bar.
- **`math.lerp`** blends `a` and `b` by `t`. **`math.inverseLerp`** runs the other way and tells you
  which `t` a value sits at.
- **`math.approach`** moves a value towards a target by at most `step` and stops there, so it never
  overshoots. Give it a step of `speed * delta` inside a step handler and it moves at that speed a
  second.
- **`math.smoothDamp`** eases a value towards a target and remembers how fast it is going. It takes
  the current value, the target, the velocity, roughly how long it should take, and the seconds since
  the last step, and returns the new value and the new velocity. Keep the velocity in a variable and
  hand it back in each time, as the example does.
- **`math.noise`** is Luau's Perlin noise. **`math.fbm`** layers it: the fourth argument is how many
  octaves to add up, and lacunarity and gain follow it.
- **`math.bezier`** reads a list of control points as a curve, and **`math.catmullRom`** reads a list
  of points the curve passes through. Both take numbers or vectors, so the same call smooths a value
  and a path.

<!-- demo:math-numbers -->

## Angles

An angle that keeps growing eventually reads as a large number rather than a direction, and
subtracting one angle from another can tell you to turn the long way round. These three functions
handle that.

```lua
angle.wrap(a)          -- into -pi to pi
angle.delta(a, b)      -- the shortest turn from a to b
angle.lerp(a, b, t)
```

- **`angle.wrap`** brings an angle back into the range -pi to pi.
- **`angle.delta`** gives the shortest turn from one angle to another, which can be negative. Use it
  to decide which way a body should turn to face something.
- **`angle.lerp`** blends two angles the short way round, so turning from just under pi to just over
  -pi crosses the gap instead of sweeping back through zero.

<!-- demo:math-angles -->

## Random

```lua
random.range(1, 5)  random.int(1, 6)  random.chance(0.25)
random.pick(list)  random.shuffle(list)
random.unit()  random.onSphere(3)  random.inSphere(3)
random.inCircle(5)                    -- flat on the ground
random.inBox(vec3(4, 1, 4))
```

- **`random.range`** gives a decimal number between the two you pass, and **`random.int`** gives a
  whole one, so the example rolls a die.
- **`random.chance`** answers true that fraction of the time: `0.25` is true one call in four.
- **`random.pick`** takes one entry out of a list, and **`random.shuffle`** reorders the list.
- **`random.unit`** gives a direction of length 1. **`random.onSphere`** gives a point on the surface
  of a sphere of that radius and **`random.inSphere`** a point anywhere inside it.
- **`random.inCircle`** gives a point inside a circle flat on the ground, with y left at 0, which is
  what you want for scattering things across a floor. **`random.inBox`** gives a point inside a box of
  that size.
