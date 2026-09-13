package com.meekdev.moud.script.host;

import com.meekdev.moud.core.chat.ChatCommand;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.CallbackDef;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Owners;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.remote.Remote;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InstanceAccess {

    private final Host host;
    private final Members shared = new Members("Instance");
    private final Map<ClassDef<?>, Members> byClass = new LinkedHashMap<>();
    private final Map<Callback, Callable> assigned = new IdentityHashMap<>();
    private final List<Signals> bundles = new ArrayList<>();
    private final ScriptInstances scripts;

    InstanceAccess(Host host) {
        this.host = host;
        this.scripts = new ScriptInstances(host);
    }

    public Members shared() {
        return shared;
    }

    public Members of(ClassDef<?> def) {
        return byClass.computeIfAbsent(def, key -> new Members(key.name()));
    }

    public Map<ClassDef<?>, Members> classMembers() {
        return byClass;
    }

    ScriptInstances scripts() {
        return scripts;
    }

    public Object get(Instance instance, String key) {
        if (!instance.isAlive()) throw new HostError("%s has been destroyed", instance.name());
        switch (key) {
            case "name" -> { return instance.name(); }
            case "className" -> { return instance.def().name(); }
            case "parent" -> { return instance.parent(); }
            case "changed" -> { return signals(instance).changed(); }
            case "childAdded" -> { return signals(instance).childAdded(); }
            case "destroying" -> { return signals(instance).destroying(); }
            default -> { }
        }
        EventDef event = instance.def().event(key);
        if (event != null) return signals(instance).named(event);
        CallbackDef callback = instance.def().callback(key);
        if (callback != null) return assigned.get(callback.on(instance));

        if (instance instanceof Spatial) {
            switch (key) {
                case "position" -> { return Transforms.world(instance).position(); }
                case "rotation" -> { return Transforms.world(instance).rotation(); }
                case "worldCframe" -> { return Transforms.world(instance); }
                default -> { }
            }
        }
        PropertyDef property = instance.def().property(key);
        if (property != null) return read(instance, property);

        Instance child = instance.child(key);
        if (child != null) return child;

        for (ClassDef<?> def = instance.def(); def != null; def = def.parent()) {
            Members members = byClass.get(def);
            if (members != null && members.has(key)) return members.get(key);
        }
        if (shared.has(key)) return shared.get(key);
        throw new HostError("%s has no member '%s'", instance.def().name(), key);
    }

    public void set(Instance instance, String key, Object value) {
        if (!instance.isAlive()) throw new HostError("%s has been destroyed", instance.name());
        CallbackDef callback = instance.def().callback(key);
        if (callback != null) {
            assign(instance, callback, value);
            return;
        }
        if (key.equals("name")) {
            if (!(value instanceof String name)) throw new HostError("name must be a string");
            Instances.rename(instance, name);
            return;
        }
        if (key.equals("parent")) {
            if (!(value instanceof Instance parent)) throw new HostError("parent must be an instance, use destroy() to remove");
            Instances.reparent(instance, parent);
            return;
        }
        if (instance instanceof Spatial spatial) {
            PropertyDef frame = instance.def().property("cframe");
            switch (key) {
                case "position" -> {
                    Instances.setObj(instance, frame, Transforms.localFor(instance,
                            Transforms.world(instance).withPosition(expect(value, Vector3.class, "position", "a vec3")))
                            .mul(CFrame.at(spatial.pivot)));
                    return;
                }
                case "rotation" -> {
                    Quat rotation = switch (value) {
                        case Quat q -> q;
                        case CFrame c -> c.rotation();
                        default -> throw new HostError("rotation expects a cframe or a quat");
                    };
                    Quat local = Transforms.localFor(instance, new CFrame(Vector3.ZERO, rotation)).rotation();
                    Instances.setObj(instance, frame, spatial.cframe.withRotation(local));
                    return;
                }
                case "worldCframe" -> {
                    Instances.setObj(instance, frame, Transforms.localFor(instance, expect(value, CFrame.class, "worldCframe", "a cframe")));
                    return;
                }
                default -> { }
            }
        }
        PropertyDef property = instance.def().property(key);
        if (property == null) throw new HostError("%s has no property '%s'", instance.def().name(), key);
        write(instance, property, value);
    }

    public void write(Instance instance, PropertyDef property, Object value) {
        checkWrite(instance, property);
        Object parsed = parse(property, value);
        switch (property.type()) {
            case BOOL -> Instances.setBool(instance, property, (Boolean) parsed);
            case INT, NUM -> Instances.setNum(instance, property, (Double) parsed);
            default -> Instances.setObj(instance, property, parsed);
        }
    }

    public void checkWrite(Instance instance, PropertyDef property) {
        if (!property.replicated() || instance.id() < 0 || !host.client()) return;
        String me = host.me();
        if (Owners.owns(me, instance)) return;
        String owner = Owners.of(instance);
        throw new HostError("cannot write %s.%s from the client%s", instance.def().name(),
                property.name(), owner.isEmpty() ? "" : " (owned by " + owner + ")");
    }

    public void checkTag(Instance instance) {
        if (instance.id() < 0 || !host.client()) return;
        if (Owners.owns(host.me(), instance)) return;
        throw new HostError("cannot tag %s from the client", instance.name());
    }

    public static Object read(Instance instance, PropertyDef property) {
        return switch (property.type()) {
            case BOOL -> property.getBool(instance);
            case INT, NUM -> property.getNum(instance);
            case ENUM -> Enums.name((Enum<?>) property.getObj(instance));
            case REF -> property.getObj(instance) instanceof Instance target && target.isAlive() ? target : null;
            default -> property.getObj(instance);
        };
    }

    public static Object parse(PropertyDef property, Object value) {
        return switch (property.type()) {
            case BOOL -> value != null && !Boolean.FALSE.equals(value);
            case INT, NUM -> expect(value, Number.class, property.name(), "a number").doubleValue();
            case STRING, ASSET -> expect(value, String.class, property.name(), "a string");
            case VEC3 -> expect(value, Vector3.class, property.name(), "a vec3");
            case COLOR -> expect(value, Color.class, property.name(), "a color");
            case UDIM2 -> expect(value, UDim2.class, property.name(), "a udim2");
            case CFRAME -> expect(value, CFrame.class, property.name(), "a cframe");
            case QUAT -> expect(value, Quat.class, property.name(), "a quat");
            case ENUM -> {
                try {
                    yield Enums.parse(property.defaultValue().getClass(), expect(value, String.class, property.name(), "a string"));
                } catch (IllegalArgumentException e) {
                    throw new HostError("%s: %s", property.name(), e.getMessage());
                }
            }
            case REF -> {
                if (value == null) yield null;
                Instance target = expect(value, Instance.class, property.name(), "an instance or nil");
                if (!target.isAlive()) throw new HostError("%s was handed a destroyed instance", property.name());
                yield target;
            }
        };
    }

    private static <T> T expect(Object value, Class<T> type, String what, String article) {
        if (type.isInstance(value)) return type.cast(value);
        throw new HostError("%s expects %s, got %s", what, article, Host.typeOf(value));
    }

    private void assign(Instance instance, CallbackDef def, Object value) {
        Callback callback = def.on(instance);
        Callable old = assigned.remove(callback);
        if (old != null) old.release();
        if (value == null) {
            callback.set(null);
            return;
        }
        if (!(value instanceof Callable fn)) {
            throw new HostError("%s.%s expects a function or nil", instance.def().name(), def.name());
        }
        Callable kept = fn.retain();
        assigned.put(callback, kept);
        String where = instance.def().name() + "." + def.name();
        callback.set(args -> host.call(kept, where, args));
    }

    private Signals signals(Instance instance) {
        if (instance.userdata instanceof Signals existing) {
            if (existing.host == host) return existing;
            existing.close();
        }
        Signals fresh = new Signals(host, instance);
        instance.userdata = fresh;
        bundles.add(fresh);
        return fresh;
    }

    void close() {
        for (Map.Entry<Callback, Callable> entry : assigned.entrySet()) {
            entry.getKey().set(null);
            entry.getValue().release();
        }
        assigned.clear();
        for (Signals bundle : bundles) bundle.close();
        bundles.clear();
    }

    public static Object[] sent(Remote.Sent sent, InstanceTree tree) {
        List<Object> out = new ArrayList<>();
        if (!sent.from().isEmpty()) out.add(bodyOf(tree, sent.from()));
        out.addAll(sent.args());
        return out.toArray();
    }

    public static Character bodyOf(InstanceTree tree, String owner) {
        if (tree == null) return null;
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character body && owner.equals(body.owner)) return body;
        }
        return null;
    }

    private static final class Signals {

        private final Host host;
        private final Instance instance;
        private final List<Signal.Connection> links = new ArrayList<>();
        private final Map<String, HostSignal> named = new HashMap<>();
        private HostSignal changed;
        private HostSignal childAdded;
        private HostSignal destroying;

        Signals(Host host, Instance instance) {
            this.host = host;
            this.instance = instance;
        }

        HostSignal changed() {
            if (changed == null) {
                HostSignal signal = changed = new HostSignal(host, "ChangedSignal", "changed");
                links.add(instance.changed().connect(property -> signal.fire(property.name())));
            }
            return changed;
        }

        HostSignal childAdded() {
            if (childAdded == null) {
                HostSignal signal = childAdded = new HostSignal(host, "InstanceSignal", "childAdded");
                links.add(instance.childAdded().connect(signal::fire));
            }
            return childAdded;
        }

        HostSignal destroying() {
            if (destroying == null) {
                HostSignal signal = destroying = new HostSignal(host, "InstanceSignal", "destroying");
                links.add(instance.destroying().connect(signal::fire));
            }
            return destroying;
        }

        HostSignal named(EventDef event) {
            HostSignal existing = named.get(event.name());
            if (existing != null) return existing;
            HostSignal signal = new HostSignal(host, "InstanceSignal", event.name());
            named.put(event.name(), signal);
            links.add(event.on(instance).connect(what -> {
                switch (what) {
                    case Remote.Sent sent -> signal.fire(sent(sent, instance.tree()));
                    case ChatCommand.Invoked typed -> signal.fire(typed.body(), typed.text(), new ArrayList<>(typed.args()));
                    default -> signal.fire(what);
                }
            }));
            return signal;
        }

        void close() {
            for (Signal.Connection link : links) link.disconnect();
            links.clear();
            if (changed != null) changed.clear();
            if (childAdded != null) childAdded.clear();
            if (destroying != null) destroying.clear();
            for (HostSignal signal : named.values()) signal.clear();
        }
    }
}
