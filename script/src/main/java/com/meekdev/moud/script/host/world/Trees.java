package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.query.Selector;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.remote.Schema;
import com.meekdev.moud.core.remote.UnreliableRemote;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.core.value.Value;
import com.meekdev.moud.core.zone.ProximityPrompt;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import com.meekdev.moud.script.host.Tasks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Trees {

    private Trees() {}

    public static void install(Host host, Members game) {
        Members shared = host.instances().shared();
        shared.method("add", "(className: string, properties: { [string]: any }?) -> Instance", a -> {
            Instance parent = a.self();
            ClassDef<?> def = type(host, a.string(1));
            Instance created = Instances.create(def, parent, def.name());
            for (Map.Entry<String, Object> entry : a.map(2, Map.of()).entrySet()) {
                host.instances().set(created, entry.getKey(), entry.getValue());
            }
            return created;
        });
        shared.method("addAll", "(className: string, properties: { { [string]: any } }) -> number", a -> {
            Instance parent = a.self();
            ClassDef<?> def = type(host, a.string(1));
            List<Object> entries = a.list(2);
            for (Object entry : entries) {
                Instance created = Instances.create(def, parent, def.name());
                if (!(entry instanceof Map<?, ?> properties)) continue;
                for (Map.Entry<?, ?> property : properties.entrySet()) {
                    host.instances().set(created, String.valueOf(property.getKey()), property.getValue());
                }
            }
            return (double) entries.size();
        });
        shared.method("children", "() -> { Instance }", a -> new ArrayList<Object>(a.self().children()));
        shared.method("find", "(name: string) -> Instance?", a -> a.self().child(a.string(1)));
        shared.method("isA", "(className: string) -> boolean", a -> a.self().isA(type(host, a.string(1))));
        shared.method("destroy", "() -> ()", a -> {
            Instances.destroy(a.self());
            return null;
        });
        shared.method("setOwner", "(to: Instance?) -> ()", a -> {
            Instance instance = a.self();
            if (host.client()) throw new HostError("setOwner is server-only");
            if (!(instance instanceof Spatial)) throw new HostError("%s cannot have an owner", instance.def().name());
            PropertyDef owner = instance.def().property("owner");
            if (!a.has(1)) {
                Instances.setObj(instance, owner, "");
                return null;
            }
            if (!(a.get(1) instanceof Character body)) throw new HostError("setOwner expects a body or nil");
            Instances.setObj(instance, owner, body.owner);
            return null;
        });
        shared.method("addTag", "(tag: string) -> ()", a -> {
            host.instances().checkTag(a.self());
            Instances.addTag(a.self(), a.string(1));
            return null;
        });
        shared.method("removeTag", "(tag: string) -> ()", a -> {
            host.instances().checkTag(a.self());
            Instances.removeTag(a.self(), a.string(1));
            return null;
        });
        shared.method("hasTag", "(tag: string) -> boolean", a -> a.self().hasTag(a.string(1)));
        shared.method("getTags", "() -> { string }", a -> new ArrayList<Object>(a.self().tags()));

        shared.method("fireServer", "(...any) -> ()", a -> {
            Remote remote = remote(a);
            if (!host.client()) throw new HostError("fireServer is client-only");
            post(host).toServer(remote.id(), declared(remote, a, 1), !(remote instanceof UnreliableRemote));
            return null;
        });
        shared.method("fireClient", "(to: Instance, ...any) -> ()", a -> {
            Remote remote = remote(a);
            if (host.client()) throw new HostError("fireClient is server-only");
            if (!(a.get(1) instanceof Character body)) throw new HostError("fireClient expects a body as the first argument");
            post(host).toClient(body.owner, remote.id(), declared(remote, a, 2), !(remote instanceof UnreliableRemote));
            return null;
        });
        shared.method("fireAllClients", "(...any) -> ()", a -> {
            Remote remote = remote(a);
            if (host.client()) throw new HostError("fireAllClients is server-only");
            post(host).toAllClients(remote.id(), declared(remote, a, 1), !(remote instanceof UnreliableRemote));
            return null;
        });

        shared.method("findFirstDescendant", "(name: string) -> Instance?", a -> first(a.self(), a.string(1)));
        shared.method("descendants", "(className: string?) -> { Instance }", a -> {
            List<Object> out = new ArrayList<>();
            descendants(a.self(), a.has(1) ? type(host, a.string(1)) : null, out);
            return out;
        });
        shared.method("childrenOfClass", "(className: string) -> { Instance }", a -> {
            ClassDef<?> def = type(host, a.string(1));
            List<Object> out = new ArrayList<>();
            for (Instance child : a.self().children()) {
                if (child.def().isA(def)) out.add(child);
            }
            return out;
        });
        shared.method("firstAncestorOfClass", "(className: string) -> Instance?", a -> {
            ClassDef<?> def = type(host, a.string(1));
            Instance up = a.self().parent();
            while (up != null && !up.def().isA(def)) up = up.parent();
            return up;
        });
        shared.method("firstAncestor", "(name: string) -> Instance?", a -> {
            String name = a.string(1);
            Instance up = a.self().parent();
            while (up != null && !up.name().equals(name)) up = up.parent();
            return up;
        });
        shared.method("isDescendantOf", "(other: Instance) -> boolean", a -> {
            Instance other = a.instance(1);
            Instance up = a.self().parent();
            while (up != null && up != other) up = up.parent();
            return up != null;
        });
        shared.method("byTag", "(tag: string) -> { Instance }", a -> {
            Instance root = a.self();
            List<Object> out = new ArrayList<>();
            for (Instance tagged : root.tree().tagged(a.string(1))) {
                for (Instance up = tagged.parent(); up != null; up = up.parent()) {
                    if (up == root) {
                        out.add(tagged);
                        break;
                    }
                }
            }
            return out;
        });
        shared.method("values", "() -> { [string]: any }", a -> {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Instance child : a.self().children()) {
                if (!(child instanceof Value)) continue;
                PropertyDef value = child.def().property("value");
                if (value != null) out.put(child.name(), host.instances().get(child, "value"));
            }
            return out;
        });
        shared.method("clone", "(parent: Instance?) -> Instance?", a -> {
            Instance original = a.self();
            Instance parent = a.instance(1, original.parent());
            if (parent == null) throw new HostError("cannot clone the root without a parent");
            Instance holder = Instances.create(Classes.FOLDER, parent, "clone");
            List<Instance> made = Scene.load(Scene.save(List.of(original)), holder, host.classes());
            Instance copy = made.isEmpty() ? null : made.getFirst();
            if (copy != null) Instances.reparent(copy, parent);
            Instances.destroy(holder);
            return copy;
        });
        shared.method("query", "(selector: string) -> { Instance }", a -> new ArrayList<Object>(select(host, a)));
        shared.method("queryFirst", "(selector: string) -> Instance?", a -> {
            List<Instance> found = select(host, a);
            return found.isEmpty() ? null : found.getFirst();
        });
        shared.method("waitForChild", "(name: string, timeout: number?) -> Instance?", a -> {
            Instance parent = a.self();
            String name = a.string(1);
            Instance existing = parent.child(name);
            if (existing != null) return existing;
            double timeout = a.number(2, -1);
            double[] waited = {0};
            return new Suspend(dt -> {
                Instance child = parent.isAlive() ? parent.child(name) : null;
                if (child != null) return new Object[] {child};
                waited[0] += dt;
                return timeout >= 0 && waited[0] >= timeout || !parent.isAlive() ? new Object[] {null} : null;
            });
        });
        shared.method("onChild", "(name: string, fn: (child: Instance) -> ()) -> Connection", a -> {
            Instance parent = a.self();
            String name = a.string(1);
            Callable fn = a.callable(2).retain();
            Instance existing = parent.child(name);
            if (existing != null) Tasks.spawn(host, fn, existing);
            HostSignal added = (HostSignal) host.instances().get(parent, "childAdded");
            return added.connect(values -> {
                if (values.length > 0 && values[0] instanceof Instance child && child.name().equals(name)) {
                    host.call(fn, "onChild", child);
                }
                return null;
            });
        });

        Members tags = new Members("Tags");
        Map<String, HostSignal> addedSignals = new HashMap<>();
        Map<String, HostSignal> removedSignals = new HashMap<>();
        List<Signal.Connection> links = new ArrayList<>();
        InstanceTree tree = host.world().tree();
        tags.method("tagged", "(tag: string) -> { Instance }", a -> new ArrayList<Object>(tree.tagged(a.string(1))));
        tags.method("added", "(tag: string) -> InstanceSignal", a -> tagSignal(host, addedSignals, links, a.string(1), tree.tagAdded(a.string(1))));
        tags.method("removed", "(tag: string) -> InstanceSignal", a -> tagSignal(host, removedSignals, links, a.string(1), tree.tagRemoved(a.string(1))));
        host.onClose(() -> links.forEach(Signal.Connection::disconnect));
        host.declare(tags);
        game.value("tags", "Tags", tags);

        Members zones = host.instances().of(Classes.ZONE);
        zones.method("players", "() -> { Instance }", a -> {
            List<Object> out = new ArrayList<>();
            for (Instance occupant : a.self(Zone.class).occupants()) {
                if (occupant instanceof Character body && body.hasPlayer()) out.add(occupant);
            }
            return out;
        });
        zones.method("occupants", "() -> { Instance }", a -> new ArrayList<Object>(a.self(Zone.class).occupants()));
        zones.method("contains", "(position: Vector3) -> boolean", a -> Zones.contains(a.self(Zone.class), a.vector(1)));

        Members zoneService = new Members("Zones")
                .method("at", "(position: Vector3) -> { Instance }", a -> new ArrayList<Object>(Zones.at(tree, a.vector(1))));
        host.declare(zoneService);
        game.value("zones", "Zones", zoneService);

        Members proximity = new Members("Proximity")
                .method("closestInteractable", "(body: Instance) -> (Instance?, number?)", a -> {
                    Vector3 at = Transforms.world(a.instance(1)).position();
                    ProximityPrompt best = null;
                    double bestDistance = Double.MAX_VALUE;
                    for (ProximityPrompt prompt : tree.ofClass(Classes.PROXIMITY_PROMPT)) {
                        if (!prompt.enabled || prompt.parent() == null) continue;
                        double d = Transforms.world(prompt.parent()).position().add(prompt.offset).distance(at);
                        if (d <= prompt.maxActivationDistance && d < bestDistance) {
                            best = prompt;
                            bestDistance = d;
                        }
                    }
                    return best == null ? null : Results.of(best, bestDistance);
                })
                .method("watch", "(a: Instance, b: Instance, range: number) -> ProximityWatcher", a -> watch(host, a))
                .function("falloff", "(distance: number, min: number, max: number, rolloff: number?) -> number", a -> {
                    double distance = a.number(0);
                    double min = a.number(1);
                    double max = a.number(2);
                    double rolloff = a.number(3, 1);
                    if (distance <= min) return 1.0;
                    if (distance >= max) return 0.0;
                    return Math.pow(1 - (distance - min) / (max - min), rolloff);
                });
        host.declare(proximity);
        host.api().declare(new Members("ProximityWatcher")
                .declare("entered", "AnySignal")
                .declare("left", "AnySignal")
                .declare("near", "boolean")
                .method("stop", "() -> ()", a -> null).decl());
        game.value("proximity", "Proximity", proximity);
    }

    private static Object watch(Host host, Args a) {
        Instance first = a.instance(1);
        Instance second = a.instance(2);
        double range = a.number(3);
        HostSignal entered = new HostSignal(host, "AnySignal", "proximity.entered");
        HostSignal left = new HostSignal(host, "AnySignal", "proximity.left");
        boolean[] near = {false};
        boolean[] stopped = {false};
        Members watcher = new Members("ProximityWatcher");
        watcher.value("entered", "AnySignal", entered)
                .value("left", "AnySignal", left)
                .field("near", "boolean", () -> near[0])
                .method("stop", "() -> ()", x -> {
                    stopped[0] = true;
                    return null;
                });
        Consumer<Double> check = dt -> {
            if (stopped[0] || !first.isAlive() || !second.isAlive() || first.parent() == null || second.parent() == null) return;
            boolean now = Transforms.world(first).position().distance(Transforms.world(second).position()) <= range;
            if (now == near[0]) return;
            near[0] = now;
            (now ? entered : left).fire(first, second);
        };
        host.onStep(check);
        host.onRenderStep(check);
        return watcher;
    }

    private static HostSignal tagSignal(Host host, Map<String, HostSignal> known, List<Signal.Connection> links,
                                        String tag, Signal<Instance> source) {
        return known.computeIfAbsent(tag, key -> {
            HostSignal signal = new HostSignal(host, "InstanceSignal", "tag " + tag);
            links.add(source.connect(signal::fire));
            return signal;
        });
    }

    private static Remote remote(Args a) {
        if (!(a.get(0) instanceof Remote remote)) throw a.error("expects to be called with ':' on a Remote");
        if (!remote.isAlive()) throw new HostError("remote '%s' was destroyed, look it up again", remote.name());
        return remote;
    }

    private static PostRef post(Host host) {
        if (host.post() == null) throw new HostError("no transport bound");
        return host.post();
    }

    private static List<Object> declared(Remote remote, Args a, int first) {
        List<Object> args = new ArrayList<>(Arrays.asList(a.from(first)));
        try {
            Schema.check(remote, args);
        } catch (IllegalArgumentException e) {
            throw new HostError(e.getMessage());
        }
        return args;
    }

    private static List<Instance> select(Host host, Args a) {
        try {
            return Selector.parse(a.string(1), host.classes()).all(a.self());
        } catch (IllegalArgumentException e) {
            throw new HostError(e.getMessage());
        }
    }

    public static ClassDef<?> type(Host host, String name) {
        ClassDef<?> def = host.classes().find(name);
        if (def == null) throw new HostError("there is no class called %s", name);
        return def;
    }

    private static Instance first(Instance at, String name) {
        for (Instance child : at.children()) {
            if (child.name().equals(name)) return child;
        }
        for (Instance child : at.children()) {
            Instance found = first(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static void descendants(Instance at, ClassDef<?> def, List<Object> out) {
        for (Instance child : at.children()) {
            if (def == null || child.def().isA(def)) out.add(child);
            descendants(child, def, out);
        }
    }
}
