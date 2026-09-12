package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;

// changed, childAdded and destroying
//
// core fires all three already, so this is only the bridge: one java handler per instance per
// signal, forwarding to whatever luau connected. both halves are made on the first read, so an
// instance a place never mentions carries neither
public final class InstanceSignals {

    private static LuaState state;
    private static Consumer<ScriptError> onError;

    private InstanceSignals() {}

    public static void install(LuaState lua, Consumer<ScriptError> errors) {
        state = lua;
        onError = errors;
    }

    public static Signals.Handlers changed(Instance instance) {
        return bundle(instance).changed(instance);
    }

    public static Signals.Handlers childAdded(Instance instance) {
        return bundle(instance).childAdded(instance);
    }

    public static Signals.Handlers destroying(Instance instance) {
        return bundle(instance).destroying(instance);
    }

    // anything the class declared as an event, by name
    //
    // one bridge for every one of them rather than a method per signal: a class an addon writes
    // gets its events across without this file learning they exist
    public static Signals.Handlers of(Instance instance, EventDef event) {
        return bundle(instance).named(instance, event);
    }

    // a bundle holds refs into one lua state and a reload opens another, so one built for a state
    // that has gone is dropped rather than fired into. core nulls userdata when the instance dies,
    // which is the whole of the lifetime
    private static Bundle bundle(Instance instance) {
        if (instance.userdata instanceof Bundle existing) {
            if (existing.state == state) return existing;
            existing.close();
        }
        Bundle fresh = new Bundle(state);
        instance.userdata = fresh;
        return fresh;
    }

    private static final class Bundle {

        private final LuaState state;
        private Signals.Handlers changed;
        private Signals.Handlers childAdded;
        private Signals.Handlers destroying;
        private Signal.Connection changedLink;
        private Signal.Connection childAddedLink;
        private Signal.Connection destroyingLink;
        private final Map<String, Signals.Handlers> named = new HashMap<>();
        private final List<Signal.Connection> namedLinks = new ArrayList<>();

        Bundle(LuaState state) {
            this.state = state;
        }

        Signals.Handlers changed(Instance instance) {
            if (changed == null) {
                Signals.Handlers handlers = changed = new Signals.Handlers();
                changedLink = instance.changed().connect(property ->
                        fire(handlers, s -> {
                            s.pushString(property.name());
                            return 1;
                        }));
            }
            return changed;
        }

        Signals.Handlers childAdded(Instance instance) {
            if (childAdded == null) {
                Signals.Handlers handlers = childAdded = new Signals.Handlers();
                childAddedLink = instance.childAdded().connect(child ->
                        fire(handlers, s -> {
                            Proxies.push(s, child);
                            return 1;
                        }));
            }
            return childAdded;
        }

        Signals.Handlers destroying(Instance instance) {
            if (destroying == null) {
                Signals.Handlers handlers = destroying = new Signals.Handlers();
                destroyingLink = instance.destroying().connect(dying ->
                        fire(handlers, s -> {
                            Proxies.push(s, dying);
                            return 1;
                        }));
            }
            return destroying;
        }

        Signals.Handlers named(Instance instance, EventDef event) {
            Signals.Handlers existing = named.get(event.name());
            if (existing != null) return existing;

            Signals.Handlers handlers = new Signals.Handlers();
            named.put(event.name(), handlers);
            namedLinks.add(event.on(instance).connect(what ->
                    fire(handlers, s -> {
                        // a channel hands over however many arguments were sent, and on the server the
                        // sender first. every other event carries one thing
                        if (what instanceof Remote.Sent sent) {
                            return Remotes.pushSent(s, sent, instance.tree());
                        }
                        Proxies.push(s, (Instance) what);
                        return 1;
                    })));
            return handlers;
        }

        private void fire(Signals.Handlers handlers, Signals.Args args) {
            Signals.fire(state, handlers, onError, args);
        }

        void close() {
            if (changedLink != null) changedLink.disconnect();
            if (childAddedLink != null) childAddedLink.disconnect();
            if (destroyingLink != null) destroyingLink.disconnect();
            for (Signal.Connection link : namedLinks) link.disconnect();
        }
    }
}
