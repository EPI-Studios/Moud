# Mixins, Java and shader patches

A place can reach into Minecraft itself. You can change what one of the game's methods does, change a
single call inside it, change a constant or a local it uses, and put your own code into the game's
shaders. All of it happens while the game runs, and all of it comes off again when the place reloads.

The names you use are the game's own. Minecraft ships unobfuscated, so the class and method names in
crash reports and in a decompiler are the ones to type.

## Classes

`java.use(name)` takes a fully qualified class name and hands you that Java class. From there you
construct objects, call methods and read fields:

```lua
local Player = java.use("net.minecraft.world.entity.player.Player")
local Items = java.use("net.minecraft.world.item.Items")
local stack = java.use("net.minecraft.world.item.ItemStack"):new(Items.DIAMOND, 3)
stack:setCount(5)
Player.getName(somePlayer)        -- a method called through the class
java.typeof(stack)  java.instanceOf(stack, "net.minecraft.world.item.ItemStack")
```

- `Class.method` is a method handle. You call it, or you hook it.
- `Class:new(...)` constructs an object, and `Class.field` reads a static field.
- On an object, `obj:method(...)` calls a method and `obj.field` reads or writes a field, private
  members included.
- Numbers, text, flags and `nil` cross as themselves, and text becomes an enum constant by name.
- `java.typeof(value)` gives the class of a Java object, and `java.instanceOf(value, name)` answers
  whether it is one.

**Overloads.** When a class has several methods with the same name, the first one whose parameters
take the arguments is called. To hook one of several, narrow it down first:

```lua
Player.hurt:overload("ServerLevel", "DamageSource", "float")
Player.hurt:overload(2)
Player.hurt:overload("(F)Z")
```

You narrow it by the parameter type names, by a number, or by the descriptor.

## Hooks

A hook is a Luau function attached to a Java method. There are four kinds, and they differ in where
they run and what returning a value means:

```lua
Player.causeFallDamage:before(function(self, distance, multiplier, source)
    if distance < 10 then return false end      -- returning a value skips the method with it
end)                                            -- returning nothing lets it run

Player.getSpeed:after(function(self, speed)
    return speed * 2                            -- returning a value replaces the result
end)

Player.jumpFromGround:replace(function(original, self)
    original(self)                              -- the real method, callable with other arguments
    original(self)
end)

SectionOcclusionGraph.update:args(function(smartCull, ...)
    return false                                -- values replace arguments in order, nil keeps one
end)
```

- **`before`** runs ahead of the method. Return nothing and the method runs as usual. Return a value
  and the method is skipped, with your value as its result. The example above skips
  `causeFallDamage` when `distance` is under 10, and lets longer falls run as usual.
- **`after`** runs once the method has finished, and is handed the result. Return a value to replace
  it, or nothing to keep it.
- **`replace`** stands in for the method. Your handler is given `original` first, which is the real
  method and takes whatever arguments you want to pass it. The example calls the real
  `jumpFromGround` twice.
- **`args`** rewrites the arguments before the method sees them. Each value you return replaces one
  argument in order, and `nil` keeps the one that was there.

`self` is the object the method was called on, and it is `nil` for a static method.

> [!IMPORTANT]
> A method must be **declared** by the class you name. Hooking a method a class only inherits is an
> error, and the error says which class to hook instead.

### Inside a method

The hooks above wrap a method. These three change what happens inside one:

```lua
-- one call: the handler gets the real call and the object it was made on
Camera.extractRenderState:redirect(Projection.getMatrix, function(original, projection, dest)
    return dest:setOrtho(-10, 10, -10, 10, -300, 600, false)
end)

LivingEntity.travel:constant(0.91, 0.98)                         -- a fixed value, no script runs
LivingEntity.travel:constant(0.91, view:ref("friction"))         -- read from a state each time
LivingEntity.travel:variable("speed", function(speed) return speed * 1.5 end)   -- before each store
```

- **`redirect`** picks one call made inside the method and sends it to you instead. Your handler gets
  `original`, then the object the call was made on and its arguments, so you can call it, change it or
  answer something else.
- **`constant`** replaces a literal value in the method's code. The first argument is the value to
  look for and the second is what to use instead, either another fixed value or a state ref that is
  read each time.
- **`variable`** watches a local variable and runs your function just before each store, with the
  value about to be written.

Where the method uses the same constant, call or local more than once, pick one with
`{ ordinal = 2 }`. A constant, call or local the method does not have is an error when the hook is
made, not a silent no-op that you find out about later.

Locals are named from the class's local variable table, which Minecraft keeps. A slot number works
too.

### Fields

`Class.fields.name:changed(handler)` watches every write to a field:

```lua
Player.fields.health:changed(function(self, old, new)
    if new < 1 then return 1 end                -- returning a value writes that instead
end)
```

The handler is given the object, the old value and the new one. Return a value and that is what gets
written, so the example above keeps a player alive at 1 health.

Every write to the field in a class that is loaded goes through the handler, including writes from
other classes and subclasses. Writes inside constructors are left alone.

> [!NOTE]
> A final field cannot be watched, and classes loaded after the watch starts are not rewritten. If a
> write you expected never reaches the handler, the class it came from was loaded after you started
> watching.

### Options

Every hook takes an options table last:

| | |
|---|---|
| `when` | a function (given `self`) or a state ref; the hook only acts while it is true |
| `once` | the hook comes off after it acted once |
| `limit` | at most this many times per tick, the method runs untouched past it |
| `priority` | higher runs first; for `replace` and `redirect`, higher is the outer one |
| `raw` | arguments stay Java objects instead of Moud values |

A hook returns a handle. You take it off with `hook:remove()`, switch it off and on with
`hook.enabled = false`, and read `hook.calls` and `hook.errors` to see how often it ran and how often
it failed.

## Moud values in hooks

Hook arguments and results arrive as Moud values, and go back converted:

| Java | Moud |
|---|---|
| `BlockPos`, `Vec3` | `vec3` |
| a player | their body |
| `Identifier` | `"minecraft:stone"` |
| `Component` | rich text |
| `ItemStack` | `{ id = "minecraft:diamond", count = 3 }` |

That means a hook on a method taking a `BlockPos` is handed a `vec3` you can do maths with, and a hook
on one taking a player is handed the body you already have API for. Pass `raw` in the options table
when you would rather have the Java objects.

The same conversions apply when calling Java, so `level:getBlockState(vec3(0, 64, 0))` works.

## State

`mixin.state(name, defaults)` makes a table that hooks can read without entering the script:

```lua
local view = mixin.state("camera", { size = 22, enabled = true })   -- one per name, shared by every file
view.size = 30
Class.method:constant(1.0, view:ref("size"))
Class.method:after(fn, { when = view:ref("enabled") })
```

- There is one state per name, shared by every file that asks for it. Two mixin files that both call
  `mixin.state("camera", ...)` get the same table.
- `view.size = 30` writes it, and `view:ref("size")` makes a reference a hook reads each time it runs.
- Reading a state ref happens in Java, so hooks that only use refs and fixed values never enter the
  script at all.

## Mixin files

`client/mixins/*.luau` and `server/mixins/*.luau` load before `main`, in name order. Saving one takes
its hooks off and runs it again, without reloading the place.

```lua
-- client/mixins/zoom.luau
local Mouse = java.use("net.minecraft.client.MouseHandler")
local view = mixin.state("camera", { size = 22, enabled = true })

return mixin "zoom" {
    when = view:ref("enabled"),                  -- shared by every hook in the group
    Mouse.onScroll:before(function(self, window, dx, dy)
        view.size = math.clamp(view.size - dy * 2, 8, 60)
        return true
    end),
}
```

- `mixin "name" { ... }` groups hooks together and gives them a name. Options written in the group,
  such as `when` above, are shared by every hook in it.
- Returning the group from the file is what ties the hooks to the file, so saving the file can take
  exactly those hooks off again.
- `return true` from the `before` handler skips Minecraft's own scroll handling, so the wheel only
  changes `view.size`, which the camera hooks in the same state read.

> [!TIP]
> The **mixins** tab of the <kbd>Right Shift</kbd> overlay lists every hook: side, file, calls and
> milliseconds per second, errors, and a switch to turn one off live. That is how you find the hook
> that is costing you frames. `mixin.hooks()` gives the same list to a script.

## Threads and errors

A hook acts only on the thread of the side that made it: server hooks on the server thread, client
hooks on the render thread.

An error in a handler goes to the script errors, and the method runs as if it were unhooked, so a
mistake in a hook does not take the game down with it. Game code called from inside a handler does not
trigger hooks again.

## Shader patches (client)

`shaders.patch(target, options)` puts your GLSL into one of the game's shaders:

```lua
local patch = shaders.patch("minecraft:core/terrain", {
    uniforms = { HoleFrom = "vec3", HoleTo = "vec3", HoleRadius = "float" },
    vertex = { at = "end", code = "moudWorld = Position + vec3(ChunkPosition);", out = { moudWorld = "vec3" } },
    fragment = { at = "start", code = [[
        if (distance(moudWorld, HoleTo) < HoleRadius) discard;
    ]] },
})
patch.HoleTo = me.position
patch.HoleRadius = 4
```

- Targets are shader ids: vanilla `minecraft:core/terrain`, Moud's `moud:instance/part`, or a list of
  them.
- Code goes `at = "start"` or `"end"` of `main`, `after = "text"` (after that statement) or
  `before = "text"` (before that line). An anchor that isn't there is reported in the script errors.
- `out` varyings are declared out in the vertex stage and in in the fragment stage. The example
  declares `moudWorld` in the vertex code and reads it in the fragment code.
- Uniforms are `float`, `int`, `bool`, `vec2`, `vec3` or `vec4`, and you set them as fields on the
  patch, the way `patch.HoleRadius = 4` does. Only changed values upload, once a frame.
- The shader runs on the GPU, so nothing scripted runs per block or per pixel. Writing a uniform once
  a frame is the whole cost.
- Patches survive resource reloads, and come off when the place stops or you call `patch:remove()`.

## Outside the dev environment

The first hook needs the JDK's instrumentation, attached when the mod starts. Minecraft's own runtime
has no `jdk.attach`, so ByteBuddy attaches through JNA, which Minecraft ships.

> [!NOTE]
> Java prints a warning about dynamic agents. A launcher that passes
> `-XX:+EnableDynamicAgentLoading` silences it, and keeps working once a future Java turns dynamic
> loading off by default.
