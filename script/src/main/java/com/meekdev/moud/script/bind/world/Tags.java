package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.script.err.ScriptError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;

public final class Tags {

    private final LuaState state;
    private final InstanceTree tree;
    private final Consumer<ScriptError> onError;
    private final Map<String, Signals.Handlers> added = new HashMap<>();
    private final Map<String, Signals.Handlers> removed = new HashMap<>();
    private final List<Signal.Connection> links = new ArrayList<>();

    public Tags(LuaState state, InstanceTree tree, Consumer<ScriptError> onError) {
        this.state = state;
        this.tree = tree;
        this.onError = onError;
    }

    public void install() {
        state.getGlobal("game");
        state.newTable();
        LuaTables.function(state, "tags", "tagged", s -> {
            List<Instance> tagged = tree.tagged(s.checkString(2));
            s.createTable(tagged.size(), 0);
            for (int n = 0; n < tagged.size(); n++) {
                Proxies.push(s, tagged.get(n));
                s.rawSetI(-2, n + 1);
            }
            return 1;
        });
        LuaTables.function(state, "tags", "added", s -> {
            String tag = s.checkString(2);
            Signals.push(s, signal(added, tag, tree.tagAdded(tag)));
            return 1;
        });
        LuaTables.function(state, "tags", "removed", s -> {
            String tag = s.checkString(2);
            Signals.push(s, signal(removed, tag, tree.tagRemoved(tag)));
            return 1;
        });
        state.rawSetField(-2, "tags");
        state.pop(1);
    }

    private Signals.Handlers signal(Map<String, Signals.Handlers> known, String tag, Signal<Instance> source) {
        return known.computeIfAbsent(tag, key -> {
            Signals.Handlers handlers = new Signals.Handlers();
            links.add(source.connect(instance -> Signals.fire(state, handlers, onError, s -> {
                Proxies.push(s, instance);
                return 1;
            })));
            return handlers;
        });
    }

    public void close() {
        for (Signal.Connection link : links) link.disconnect();
        links.clear();
    }
}
