# Other languages

A place is Luau by default, but the engine under it does not know Luau. Every global, instance, signal
and hook is described once, and that description is handed to whichever language runs the place. A
language comes from an addon, and Moud ships two as examples: **Revo** and **Java**.

## Picking the language

You do not tell Moud which language you are using. It looks for the entry file with each known
extension and runs the place in the language that has it:

```
place/
  server/main.rv          a Revo place
  client/main.rv
```

- `place.toml` entries can leave the extension off (`server = "res://server/Main"`), and so can
  `require`. That way the same entry works whatever language the place ends up in.
- Mixin files follow the place: a Revo place uses `client/mixins/*.rv`.

> [!IMPORTANT]
> A place with a `main` in two languages is an error, because Moud cannot choose between them. If you
> are porting a place, move the old entry file out of the folder rather than leaving both in place.

## Revo

Revo looks like Luau with `const` and `fn`, and it calls the same API:

```revo
const world = game.world
const floor = world:add("Part", {name = "floor", size = vec3(20, 1, 20), position = vec3(0, 64, 0)})

game.stepped:connect(fn(dt) do
    floor.transparency = 0.5 + 0.5 * math.sin(clock())
end)

task.spawn(fn() do
    task.wait(2)
    print("two seconds in")
end)
```

- The names are the same as in Luau: `game`, `task`, `vec3`, `signal`, `java`, `mixin`, `shaders` and
  the rest. What you learn on one page of these docs is what you write in Revo.
- A place is **type checked** against the Moud api before it runs, so a wrong argument or an unknown
  field stops it with the line. Editors read `.moud/moud.d.rv`, written when the place starts in dev.
- `task.wait` works inside `task.spawn`, `task.delay` or a `Script` instance, the same as in Luau. On
  the main chunk it says so instead of waiting.
- `task.spawn` and `task.delay` take the function only, with no extra arguments after it.
- The addon embeds Revo as a native library, built with Zig from a Revo checkout next to Moud.

## Java

A Java place is one public class per file, extending `PlaceScript`:

```java
import com.meekdev.moud.addon.java.PlaceScript;
import com.meekdev.moud.core.instance.Instance;
import moud.Api;

public class Main extends PlaceScript {

    @Override
    public void run() {
        Instance floor = add(world(), "Part", props("name", "floor", "size", vec3(20, 1, 20)));
        onStep(dt -> set(floor, "transparency", 0.5 + 0.5 * Math.sin(Api.clock())));
        spawn(() -> {
            sleep(2);
            print("two seconds in");
        });
    }
}
```

- `run()` is the entry point, and it does what the top of a Luau file does.
- `PlaceScript` has the everyday helpers: `world()`, `script()`, `add`, `get`, `set`, `call`, `tween`,
  `connect`, `onStep`, `onRenderStep`, `spawn`, `delay`, `every`, `sleep`, `props`, `vec3`, `color`,
  `euler`, `at`, `print`. Properties go through `get` and `set` because Java has no property syntax,
  and `props` builds the table `add` takes.
- **`moud.Api`** is the whole api with Java types, generated from the same description as the Luau
  types: `Api.signal("hit")`, `Api.cooldown().ready(body, "dash", 1)`,
  `Api.Methods.Instance.addTag(part, "hot")`.
- Waiting is `sleep(seconds)`, because `wait` belongs to `Object`. Like everywhere, it works inside
  `spawn`, `delay` or a `Script`.
- `game.persist` is a real `Map`.

> [!NOTE]
> Each file is compiled when the place loads, so a Java place needs a JDK, not only a JRE.

> [!TIP]
> `moud.Api` is written to `.moud/java/moud/Api.java`, with the classpath in
> `.moud/java/classpath.txt`, so an IDE completes it the way your editor completes
> `.moud/types.d.luau` in a Luau place.

## Writing a language addon

An addon is a Fabric mod with a `moud:addon` entrypoint. A language addon also implements
`LanguageAddon`:

```java
public final class MyAddon implements Addon, LanguageAddon {
    public String id() { return "moud-mylang"; }
    public ScriptLanguage language() { return new MyLanguage(); }
}
```

```json
"entrypoints": { "moud:addon": ["com.example.MyAddon"] },
"depends": { "moud": "*" }
```

`ScriptLanguage` says what the language is called, which extensions it owns, how to start an engine
for a `Host`, and optionally how to write editor types:

```java
public interface ScriptLanguage {
    String name();
    List<String> extensions();
    ScriptEngine start(Host host);
    default void writeTypes(Path place, Api api, ClassRegistry classes) throws IOException {}
}
```

The extensions you return here are the ones Moud looks for when it picks the language for a place.
`writeTypes` has a default that does nothing, so you can leave editor types until later.

The engine runs chunks and modules, turns host functions into callable values and back, and gives the
host fibers so `wait` can park:

```java
public interface ScriptEngine extends AutoCloseable {
    void run(String chunk, String source);
    Object module(String chunk, String source);
    Fiber fiber(Callable fn);
    Fiber script(String chunk, String source, Instance script);
    ScriptValue table(Map<String, Object> data);
    Map<String, Object> read(ScriptValue table);
    void close();
}
```

What the engine gets from the host, all language neutral:

- `host.globals()`: every global value. `Builtin` is a function, `HostObject` an object with `get` and
  `set`, `Invocable` an object that can be called, `Instance`, `Vector3`, `CFrame` and the other
  values cross as themselves. `host.index`, `host.assign`, `host.invoke` and `host.operate` do the
  work for any of them.
- `host.extensions()`: members to add onto a global the language already has (Luau's `math`).
- `host.api()`: the typed description of all of it, the input for `writeTypes`.
- `host.readScript(path)`: resolves a `require` path with the language's own extensions.
- A host function that waits returns a `Suspend`. A language with coroutines parks the fiber. One
  without can use `ThreadFiber`, which is how the Java addon does it.
- `host.onStep` and `host.onRenderStep` hand the engine a tick when it needs to pump its own
  scheduler.

The Revo addon (`addons/revo`) is the example to copy for a language with its own VM, and the Java
addon (`addons/java`) for one that compiles to the JVM. Each has an example place in `example/place`.
