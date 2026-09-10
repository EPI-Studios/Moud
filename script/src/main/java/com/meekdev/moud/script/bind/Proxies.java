package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Proxies {

    static final int TAG = 1;

    private static final String METHODS = "moud.methods";

    private static final Set<String> NAMES = new LinkedHashSet<>();

    private static final Map<String, Set<String>> CLASS_NAMES = new LinkedHashMap<>();

    private static ClassRegistry classes;

    private Proxies() {}

    public static void install(LuaState state, ClassRegistry registry) {
        classes = registry;

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Proxies::index, "Instance.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Proxies::newIndex, "Instance.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.pushFunction(LuaFunc.wrap(Proxies::name, "Instance.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.pushFunction(LuaFunc.wrap(Proxies::same, "Instance.__eq"));
        state.rawSetField(-2, "__eq");
        state.setUserDataMetaTable(TAG);

        // shared method table, so __index hands back the same function rather than a new closure
        state.newTable();
        method(state, "add", Proxies::add);
        method(state, "addAll", Proxies::addAll);
        method(state, "children", Proxies::children);
        method(state, "find", Proxies::find);
        method(state, "isA", Proxies::isA);
        method(state, "destroy", Proxies::destroy);
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);
    }

    private static String methodsOf(ClassDef<?> def) {
        return "moud.methods." + def.name();
    }

    // methods only one class has. the table is per vm like every other registry entry, so a
    // client vm can carry the camera's verbs and a server vm never sees them
    public static void classMethods(LuaState state, ClassDef<?> def,
                                    Map<String, ToIntFunction<LuaState>> methods) {
        state.newTable();
        for (Map.Entry<String, ToIntFunction<LuaState>> entry : methods.entrySet()) {
            CLASS_NAMES.computeIfAbsent(def.name(), name -> new LinkedHashSet<>())
                    .add(entry.getKey());
            state.pushFunction(LuaFunc.wrap(entry.getValue(), def.name() + ":" + entry.getKey()));
            state.rawSetField(-2, entry.getKey());
        }
        state.rawSetField(LuaState.REGISTRY_INDEX, methodsOf(def));
    }

    public static Set<String> methodNames(String className) {
        return Collections.unmodifiableSet(
                CLASS_NAMES.getOrDefault(className, new LinkedHashSet<>()));
    }

    // every method an instance carries passes through here, so the names the type declarations
    // promise are the names that were actually registered rather than a second list to keep
    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        state.pushFunction(LuaFunc.wrap(body, "Instance:" + name));
        state.rawSetField(-2, name);
    }

    public static Set<String> methodNames() {
        return Collections.unmodifiableSet(NAMES);
    }

    // a proxy is not cached. caching one per instance pins a jni global ref for every instance
    // that a script ever touches, and building a scene then degrades as the ref table grows.
    // identity is __eq on the instance behind the proxy instead, which is what a place observes
    public static void push(LuaState state, Instance instance) {
        state.newUserDataTaggedWithMetatable(instance, TAG);
    }

    private static int same(LuaState state) {
        state.pushBoolean(state.toUserDataTagged(1, TAG) == state.toUserDataTagged(2, TAG));
        return 1;
    }

    private static Instance self(LuaState state) {
        Instance instance = (Instance) state.toUserDataTagged(1, TAG);
        if (instance == null) throw state.error("not an instance");
        // a destroyed instance is a bug in the place, not something to paper over
        if (!instance.isAlive()) throw state.error("%s has been destroyed", instance.name());
        return instance;
    }

    private static int index(LuaState state) {
        Instance instance = self(state);
        String key = state.checkString(2);

        switch (key) {
            case "name" -> { state.pushString(instance.name()); return 1; }
            case "className" -> { state.pushString(instance.def().name()); return 1; }
            case "parent" -> {
                Instance parent = instance.parent();
                if (parent == null) state.pushNil(); else push(state, parent);
                return 1;
            }
            case "changed" -> {
                Signals.push(state, InstanceSignals.changed(instance));
                return 1;
            }
            case "childAdded" -> {
                Signals.push(state, InstanceSignals.childAdded(instance));
                return 1;
            }
            case "destroying" -> {
                Signals.push(state, InstanceSignals.destroying(instance));
                return 1;
            }
            default -> { }
        }

        if (instance instanceof Spatial spatial) {
            // cframe is the local frame, matching the field. the composed one is its own name so
            // neither reading is a silent surprise
            if (key.equals("position")) {
                Values.push(state, spatial.cframe.position());
                return 1;
            }
            if (key.equals("worldCframe")) {
                Values.push(state, Transforms.world(instance));
                return 1;
            }
        }

        PropertyDef property = instance.def().property(key);
        if (property != null) {
            read(state, instance, property);
            return 1;
        }

        Instance child = instance.child(key);
        if (child != null) {
            push(state, child);
            return 1;
        }

        // a class may carry methods of its own, and a subclass inherits its parent's, so the
        // chain is walked before the table every instance shares
        for (ClassDef<?> def = instance.def(); def != null; def = def.parent()) {
            // most classes register nothing, and indexing the nil that leaves on the stack is a
            // native abort rather than an error a place could see
            if (state.rawGetField(LuaState.REGISTRY_INDEX, methodsOf(def)) != LuaType.TABLE) {
                state.pop(1);
                continue;
            }
            if (state.rawGetField(-1, key) != LuaType.NIL) {
                state.remove(-2);
                return 1;
            }
            state.pop(2);
        }

        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        if (state.rawGetField(-1, key) != LuaType.NIL) {
            state.remove(-2);
            return 1;
        }
        throw state.error("%s has no member '%s'", instance.def().name(), key);
    }

    private static int newIndex(LuaState state) {
        apply(state, self(state), state.checkString(2), 3);
        return 0;
    }

    // world:add("Part", { size = ..., position = ... })
    private static int add(LuaState state) {
        Instance parent = self(state);
        String className = state.checkString(2);
        ClassDef<?> def = classes.require(className);
        Instance created = Instances.create(def, parent, className);

        if (!state.isNoneOrNil(3)) {
            state.pushNil();
            while (state.next(3)) {
                apply(state, created, state.checkString(-2), state.absIndex(-1));
                state.pop(1);
            }
        }
        push(state, created);
        return 1;
    }

    // one crossing for the whole list instead of one per instance. the same argument 15.4 makes
    // for worldgen: the per call cost is a floor, so the api has to describe many at once.
    // it hands back a count rather than the instances, because a proxy each would put the cost
    // straight back. a place that needs them can walk children
    private static int addAll(LuaState state) {
        Instance parent = self(state);
        String className = state.checkString(2);
        ClassDef<?> def = classes.require(className);

        int count = state.len(3);
        for (int n = 1; n <= count; n++) {
            state.rawGetI(3, n);
            int entry = state.absIndex(-1);
            Instance created = Instances.create(def, parent, className);
            state.pushNil();
            while (state.next(entry)) {
                apply(state, created, state.checkString(-2), state.absIndex(-1));
                state.pop(1);
            }
            state.pop(1);
        }
        state.pushInteger(count);
        return 1;
    }

    private static int destroy(LuaState state) {
        Instances.destroy(self(state));
        return 0;
    }

    // an array of proxies, because reaching a child by name is no use to a place that does not
    // know the names. one crossing per child is what a list costs, which is why anything that
    // builds rather than reads belongs in addAll instead (19.4)
    private static int children(LuaState state) {
        List<Instance> children = self(state).children();
        state.createTable(children.size(), 0);
        for (int i = 0; i < children.size(); i++) {
            push(state, children.get(i));
            state.rawSetI(-2, i + 1);
        }
        return 1;
    }

    // nil rather than an error, which is the difference between asking whether a child is there
    // and reaching for one that has to be
    private static int find(LuaState state) {
        Instance child = self(state).child(state.checkString(2));
        if (child == null) state.pushNil(); else push(state, child);
        return 1;
    }

    private static int isA(LuaState state) {
        state.pushBoolean(self(state).isA(classes.require(state.checkString(2))));
        return 1;
    }

    private static int name(LuaState state) {
        state.pushString(self(state).name());
        return 1;
    }

    // the one place a member is written, so :add and assignment can never drift apart
    private static void apply(LuaState state, Instance instance, String key, int value) {
        if (key.equals("name")) {
            Instances.rename(instance, state.checkString(value));
            return;
        }
        // the tree is moved by assignment like everything else. detaching is destroy, so there is
        // one way to remove an instance rather than two that mean different things
        if (key.equals("parent")) {
            Instance parent = (Instance) state.toUserDataTagged(value, TAG);
            if (parent == null) throw state.error("parent wants an instance, use destroy to detach");
            Instances.reparent(instance, parent);
            return;
        }
        if (instance instanceof Spatial spatial) {
            PropertyDef frame = instance.def().property("cframe");
            if (key.equals("position")) {
                Instances.setObj(instance, frame, spatial.cframe.withPosition(Values.vec3(state, value)));
                return;
            }
            if (key.equals("worldCframe")) {
                Instances.setObj(instance, frame, Transforms.localFor(instance, Values.cframe(state, value)));
                return;
            }
        }

        PropertyDef property = instance.def().property(key);
        if (property == null) throw state.error("%s has no property '%s'", instance.def().name(), key);
        write(state, instance, property, value);
    }

    // a reference to something destroyed reads as nil rather than as a proxy that errors on
    // touch: a place holding a ref to a part someone removed asked a reasonable question
    private static void ref(LuaState state, Instance target) {
        if (target == null || !target.isAlive()) state.pushNil(); else push(state, target);
    }

    private static Instance refOf(LuaState state, PropertyDef property, int value) {
        if (state.isNoneOrNil(value)) return null;
        Instance target = (Instance) state.toUserDataTagged(value, TAG);
        if (target == null) throw state.error("%s wants an instance or nil", property.name());
        if (!target.isAlive()) throw state.error("%s was handed a destroyed instance", property.name());
        return target;
    }

    // the default is an instance of the enum, so it names the type without the class def
    // having to carry one
    private static Enum<?> enumOf(LuaState state, PropertyDef property, int value) {
        try {
            return Enums.parse(property.defaultValue().getClass(), state.checkString(value));
        } catch (IllegalArgumentException e) {
            throw state.error("%s: %s", property.name(), e.getMessage());
        }
    }

    private static void read(LuaState state, Instance instance, PropertyDef property) {
        switch (property.type()) {
            case BOOL -> state.pushBoolean(property.getBool(instance));
            case INT, NUM -> state.pushNumber(property.getNum(instance));
            case STRING -> state.pushString((String) property.getObj(instance));
            case VEC3 -> Values.push(state, (Vec3) property.getObj(instance));
            case COLOR -> Values.push(state, (Color) property.getObj(instance));
            case CFRAME -> Values.push(state, (CFrame) property.getObj(instance));
            case ENUM -> state.pushString(Enums.name((Enum<?>) property.getObj(instance)));
            case REF -> ref(state, (Instance) property.getObj(instance));
            default -> throw state.error("%s is not a value luau can read yet", property.name());
        }
    }

    private static void write(LuaState state, Instance instance, PropertyDef property, int value) {
        switch (property.type()) {
            case BOOL -> Instances.setBool(instance, property, state.toBoolean(value));
            case INT, NUM -> Instances.setNum(instance, property, state.checkNumber(value));
            case STRING -> Instances.setObj(instance, property, state.checkString(value));
            case VEC3 -> Instances.setObj(instance, property, Values.vec3(state, value));
            case COLOR -> Instances.setObj(instance, property, Values.color(state, value));
            case CFRAME -> Instances.setObj(instance, property, Values.cframe(state, value));
            case ENUM -> Instances.setObj(instance, property, enumOf(state, property, value));
            case REF -> Instances.setObj(instance, property, refOf(state, property, value));
            default -> throw state.error("%s is not a value luau can write yet", property.name());
        }
    }
}
