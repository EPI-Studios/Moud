package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.CallbackDef;
import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Owners;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.ArrayList;
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
import com.meekdev.moud.script.bind.world.QueryMethods;
import com.meekdev.moud.script.bind.remote.Remotes;

public final class Proxies {

    public static final int TAG = 1;

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

        state.newTable();
        method(state, "fireServer", Proxies::fireServer);
        method(state, "fireClient", Proxies::fireClient);
        method(state, "fireAllClients", Proxies::fireAllClients);
        method(state, "add", Proxies::add);
        method(state, "addAll", Proxies::addAll);
        method(state, "children", Proxies::children);
        method(state, "find", Proxies::find);
        method(state, "isA", Proxies::isA);
        method(state, "destroy", Proxies::destroy);
        method(state, "raycast", Proxies::raycast);
        method(state, "spherecast", s -> QueryMethods.spherecast(s, self(s)));
        method(state, "blockcast", s -> QueryMethods.blockcast(s, self(s)));
        method(state, "partsInBox", s -> QueryMethods.partsInBox(s, self(s)));
        method(state, "partsInRadius", s -> QueryMethods.partsInRadius(s, self(s)));
        method(state, "partsInPart", s -> QueryMethods.partsInPart(s, self(s)));
        method(state, "raycastAll", s -> QueryMethods.raycastAll(s, self(s)));
        method(state, "raycastMany", s -> QueryMethods.raycastMany(s, self(s)));
        method(state, "setOwner", Proxies::setOwner);
        method(state, "addTag", s -> {
            Instance instance = self(s);
            tagAllowed(s, instance);
            Instances.addTag(instance, s.checkString(2));
            return 0;
        });
        method(state, "removeTag", s -> {
            Instance instance = self(s);
            tagAllowed(s, instance);
            Instances.removeTag(instance, s.checkString(2));
            return 0;
        });
        method(state, "hasTag", s -> {
            s.pushBoolean(self(s).hasTag(s.checkString(2)));
            return 1;
        });
        method(state, "getTags", s -> {
            List<String> tags = new ArrayList<>(self(s).tags());
            s.createTable(tags.size(), 0);
            for (int n = 0; n < tags.size(); n++) {
                s.pushString(tags.get(n));
                s.rawSetI(-2, n + 1);
            }
            return 1;
        });
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);
    }

    private static String methodsOf(ClassDef<?> def) {
        return "moud.methods." + def.name();
    }

    public static void classMethods(LuaState state, ClassDef<?> def,
                                    Map<String, ToIntFunction<LuaState>> methods) {
        classMethods(state, def, methods, false);
    }

    public static void classMethods(LuaState state, ClassDef<?> def,
                                    Map<String, ToIntFunction<LuaState>> methods, boolean adding) {
        if (adding && state.rawGetField(LuaState.REGISTRY_INDEX, methodsOf(def)) == LuaType.TABLE) {
            for (Map.Entry<String, ToIntFunction<LuaState>> entry : methods.entrySet()) {
                CLASS_NAMES.computeIfAbsent(def.name(), name -> new LinkedHashSet<>()).add(entry.getKey());
                state.pushFunction(LuaFunc.wrap(entry.getValue(), def.name() + ":" + entry.getKey()));
                state.rawSetField(-2, entry.getKey());
            }
            state.pop(1);
            return;
        }
        if (adding) state.pop(1);
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

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        state.pushFunction(LuaFunc.wrap(body, "Instance:" + name));
        state.rawSetField(-2, name);
    }

    public static void extraMethod(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        state.pushFunction(LuaFunc.wrap(body, "Instance:" + name));
        state.rawSetField(-2, name);
        state.pop(1);
    }

    public static ClassRegistry registry() {
        return classes;
    }

    public static void luauMethod(LuaState state, String name, int function) {
        NAMES.add(name);
        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        state.pushValue(function);
        state.rawSetField(-2, name);
        state.pop(1);
    }

    public static Set<String> methodNames() {
        return Collections.unmodifiableSet(NAMES);
    }

    public static void push(LuaState state, Instance instance) {
        state.newUserDataTaggedWithMetatable(instance, TAG);
    }

    private static int fireServer(LuaState state) {
        if (!(self(state) instanceof Remote remote)) throw state.error("fireServer is a Remote's");
        return Remotes.fireServer(state, remote);
    }

    private static int fireClient(LuaState state) {
        if (!(self(state) instanceof Remote remote)) throw state.error("fireClient is a Remote's");
        return Remotes.fireClient(state, remote);
    }

    private static int fireAllClients(LuaState state) {
        if (!(self(state) instanceof Remote remote)) {
            throw state.error("fireAllClients is a Remote's");
        }
        return Remotes.fireAllClients(state, remote);
    }

    private static int same(LuaState state) {
        state.pushBoolean(state.toUserDataTagged(1, TAG) == state.toUserDataTagged(2, TAG));
        return 1;
    }

    private static Instance self(LuaState state) {
        Instance instance = (Instance) state.toUserDataTagged(1, TAG);
        if (instance == null) throw state.error("not an instance");
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
                Signals.push(state, InstanceSignals.changed(state, instance));
                return 1;
            }
            case "childAdded" -> {
                Signals.push(state, InstanceSignals.childAdded(state, instance));
                return 1;
            }
            case "destroying" -> {
                Signals.push(state, InstanceSignals.destroying(state, instance));
                return 1;
            }
            default -> { }
        }

        EventDef event = instance.def().event(key);
        if (event != null) {
            Signals.push(state, InstanceSignals.of(state, instance, event));
            return 1;
        }
        CallbackDef callback = instance.def().callback(key);
        if (callback != null) {
            Callbacks.push(state, instance, callback);
            return 1;
        }

        if (instance instanceof Spatial spatial) {
            if (key.equals("position")) {
                Values.push(state, Transforms.world(instance).position());
                return 1;
            }
            if (key.equals("rotation")) {
                Values.push(state, Transforms.world(instance).rotation());
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

        for (ClassDef<?> def = instance.def(); def != null; def = def.parent()) {
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

    private static int children(LuaState state) {
        List<Instance> children = self(state).children();
        state.createTable(children.size(), 0);
        for (int i = 0; i < children.size(); i++) {
            push(state, children.get(i));
            state.rawSetI(-2, i + 1);
        }
        return 1;
    }

    private static int find(LuaState state) {
        Instance child = self(state).child(state.checkString(2));
        if (child == null) state.pushNil(); else push(state, child);
        return 1;
    }

    public static void blocks(LuaState state, BlockRef blocks) {
        QueryMethods.blocks(state, blocks);
    }

    public static void forgetBlocks(LuaState state) {
        QueryMethods.forget(state);
    }

    private static int raycast(LuaState state) {
        return QueryMethods.raycast(state, self(state));
    }

    private static int isA(LuaState state) {
        state.pushBoolean(self(state).isA(classes.require(state.checkString(2))));
        return 1;
    }

    private static int name(LuaState state) {
        state.pushString(self(state).name());
        return 1;
    }

    private static void apply(LuaState state, Instance instance, String key, int value) {
        CallbackDef callback = instance.def().callback(key);
        if (callback != null) {
            Callbacks.assign(state, instance, callback, value);
            return;
        }
        if (key.equals("name")) {
            Instances.rename(instance, state.checkString(value));
            return;
        }
        if (key.equals("parent")) {
            Instance parent = (Instance) state.toUserDataTagged(value, TAG);
            if (parent == null) throw state.error("parent must be an instance, use destroy() to remove");
            Instances.reparent(instance, parent);
            return;
        }
        if (instance instanceof Spatial spatial) {
            PropertyDef frame = instance.def().property("cframe");
            if (key.equals("position")) {
                Instances.setObj(instance, frame, Transforms.localFor(instance,
                        Transforms.world(instance).withPosition(Values.vec3(state, value)))
                        .mul(CFrame.at(spatial.pivot)));
                return;
            }
            if (key.equals("rotation")) {
                Quat local = Transforms.localFor(instance,
                        new CFrame(Vector3.ZERO, rotationOf(state, value))).rotation();
                Instances.setObj(instance, frame, spatial.cframe.withRotation(local));
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

    private static int setOwner(LuaState state) {
        Instance instance = self(state);
        if (Remotes.onClient(state)) {
            throw state.error("setOwner is server-only");
        }
        if (!(instance instanceof Spatial)) {
            throw state.error("%s cannot have an owner",
                    instance.def().name());
        }
        PropertyDef owner = instance.def().property("owner");
        if (state.isNoneOrNil(2)) {
            Instances.setObj(instance, owner, "");
            return 0;
        }
        Instance who = (Instance) state.toUserDataTagged(2, TAG);
        if (!(who instanceof Character body)) {
            throw state.error("setOwner expects a body or nil");
        }
        Instances.setObj(instance, owner, body.owner);
        return 0;
    }

    private static void tagAllowed(LuaState state, Instance instance) {
        if (instance.id() < 0 || !Remotes.onClient(state)) return;
        if (Owners.owns(Remotes.me(state), instance)) return;
        throw state.error("cannot tag %s from the client",
                instance.name());
    }

    private static void allowed(LuaState state, Instance instance, PropertyDef property) {
        if (!property.replicated() || instance.id() < 0) return;
        if (!Remotes.onClient(state)) return;
        String me = Remotes.me(state);
        if (Owners.owns(me, instance)) return;
        String owner = Owners.of(instance);
        throw state.error("cannot write %s.%s from the client%s", instance.def().name(),
                property.name(), owner.isEmpty() ? "" : " (owned by " + owner + ")");
    }

    private static void ref(LuaState state, Instance target) {
        if (target == null || !target.isAlive()) state.pushNil(); else push(state, target);
    }

    private static Instance refOf(LuaState state, PropertyDef property, int value) {
        if (state.isNoneOrNil(value)) return null;
        Instance target = (Instance) state.toUserDataTagged(value, TAG);
        if (target == null) throw state.error("%s expects an instance or nil", property.name());
        if (!target.isAlive()) throw state.error("%s was handed a destroyed instance", property.name());
        return target;
    }

    private static Quat rotationOf(LuaState state, int value) {
        Object quat = state.toUserDataTagged(value, Values.QUAT);
        if (quat instanceof Quat q) return q;
        Object frame = state.toUserDataTagged(value, Values.CFRAME);
        if (frame instanceof CFrame cf) return cf.rotation();
        throw state.error("rotation expects a cframe or a quat");
    }

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
            case VEC3 -> Values.push(state, (Vector3) property.getObj(instance));
            case COLOR -> Values.push(state, (Color) property.getObj(instance));
            case UDIM2 -> Values.push(state, (UDim2) property.getObj(instance));
            case CFRAME -> Values.push(state, (CFrame) property.getObj(instance));
            case ENUM -> state.pushString(Enums.name((Enum<?>) property.getObj(instance)));
            case REF -> ref(state, (Instance) property.getObj(instance));
            default -> throw state.error("%s is not a value luau can read yet", property.name());
        }
    }

    private static void write(LuaState state, Instance instance, PropertyDef property, int value) {
        allowed(state, instance, property);
        Object parsed = parse(state, property, value);
        switch (property.type()) {
            case BOOL -> Instances.setBool(instance, property, (Boolean) parsed);
            case INT, NUM -> Instances.setNum(instance, property, (Double) parsed);
            default -> Instances.setObj(instance, property, parsed);
        }
    }

    public static Object parse(LuaState state, PropertyDef property, int value) {
        return switch (property.type()) {
            case BOOL -> state.toBoolean(value);
            case INT, NUM -> state.checkNumber(value);
            case STRING, ASSET -> state.checkString(value);
            case VEC3 -> Values.vec3(state, value);
            case COLOR -> Values.color(state, value);
            case UDIM2 -> Values.udim2(state, value);
            case CFRAME -> Values.cframe(state, value);
            case ENUM -> enumOf(state, property, value);
            case REF -> refOf(state, property, value);
            default -> throw state.error("unsupported value type %s", property.name());
        };
    }

    public static void checkWrite(LuaState state, Instance instance, PropertyDef property) {
        allowed(state, instance, property);
    }
}
