package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.chat.ChatCommand;
import com.meekdev.moud.core.remote.Remote;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;

public final class InstanceSignals {

    private static final Map<LuaState, Consumer<ScriptError>> ERRORS = new HashMap<>();

    private InstanceSignals() {}

    public static void install(LuaState lua, Consumer<ScriptError> errors) {
        ERRORS.put(lua.mainThread(), errors);
    }

    public static void forget(LuaState lua) {
        ERRORS.remove(lua.mainThread());
    }

    public static Signals.Handlers changed(LuaState lua, Instance instance) {
        return bundle(lua, instance).changed(instance);
    }

    public static Signals.Handlers childAdded(LuaState lua, Instance instance) {
        return bundle(lua, instance).childAdded(instance);
    }

    public static Signals.Handlers destroying(LuaState lua, Instance instance) {
        return bundle(lua, instance).destroying(instance);
    }

    public static Signals.Handlers of(LuaState lua, Instance instance, EventDef event) {
        return bundle(lua, instance).named(instance, event);
    }

    private static Bundle bundle(LuaState lua, Instance instance) {
        LuaState main = lua.mainThread();
        if (instance.userdata instanceof Bundle existing) {
            if (existing.state.equals(main)) return existing;
            existing.close();
        }
        Bundle fresh = new Bundle(main, ERRORS.getOrDefault(main, error -> {}));
        instance.userdata = fresh;
        return fresh;
    }

    private static final class Bundle {

        private final LuaState state;
        private final Consumer<ScriptError> onError;
        private Signals.Handlers changed;
        private Signals.Handlers childAdded;
        private Signals.Handlers destroying;
        private Signal.Connection changedLink;
        private Signal.Connection childAddedLink;
        private Signal.Connection destroyingLink;
        private final Map<String, Signals.Handlers> named = new HashMap<>();
        private final List<Signal.Connection> namedLinks = new ArrayList<>();

        Bundle(LuaState state, Consumer<ScriptError> onError) {
            this.state = state;
            this.onError = onError;
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
                        if (what instanceof Remote.Sent sent) {
                            return Remotes.pushSent(s, sent, instance.tree());
                        }
                        if (what instanceof ChatCommand.Invoked typed) {
                            Proxies.push(s, typed.body());
                            s.pushString(typed.text());
                            s.createTable(typed.args().size(), 0);
                            for (int n = 0; n < typed.args().size(); n++) {
                                s.pushString(typed.args().get(n));
                                s.rawSetI(-2, n + 1);
                            }
                            return 3;
                        }
                        Plain.push(s, what);
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
