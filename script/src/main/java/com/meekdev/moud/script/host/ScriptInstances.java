package com.meekdev.moud.script.host;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.script.LocalScript;
import com.meekdev.moud.core.script.Script;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class ScriptInstances {

    private final Host host;
    private final Set<Instance> running = Collections.newSetFromMap(new IdentityHashMap<>());

    ScriptInstances(Host host) {
        this.host = host;
    }

    void poll(InstanceTree tree) {
        if (host.engine() == null || tree == null) return;
        for (Instance instance : new ArrayList<>(running)) {
            if (!instance.isAlive() || instance.tree() != tree || !enabled(instance)) {
                running.remove(instance);
                host.ownership().release(instance);
            }
        }
        List<? extends Instance> scripts = host.client() ? tree.ofClass(Classes.LOCAL_SCRIPT) : tree.ofClass(Classes.SCRIPT);
        for (Instance instance : new ArrayList<>(scripts)) {
            if (enabled(instance) && running.add(instance)) start(instance);
        }
    }

    private static boolean enabled(Instance instance) {
        return switch (instance) {
            case Script s -> s.enabled;
            case LocalScript s -> s.enabled;
            default -> false;
        };
    }

    private void start(Instance instance) {
        String name = where(instance);
        String code;
        try {
            code = source(instance);
        } catch (IllegalArgumentException e) {
            host.error(name, e);
            return;
        }
        if (code == null) {
            host.error(name, new HostError("has no code and its source is not there"));
            return;
        }
        Object before = host.ownership().enter(instance);
        try {
            Fiber fiber = host.engine().script(name, code, instance);
            if (fiber != null) host.scheduler().start(fiber, instance);
        } catch (RuntimeException e) {
            host.error(name, e);
        } finally {
            host.ownership().leave(before);
        }
    }

    private String source(Instance instance) {
        String code = instance instanceof Script s ? s.code : ((LocalScript) instance).code;
        if (!code.isEmpty()) return code;
        String source = instance instanceof Script s ? s.source : ((LocalScript) instance).source;
        if (source.isEmpty()) return null;
        Host.Script script = host.readScript(Res.script(source));
        return script == null ? null : script.code();
    }

    private static String where(Instance instance) {
        StringBuilder path = new StringBuilder(instance.name());
        for (Instance at = instance.parent(); at != null; at = at.parent()) path.insert(0, at.name() + "/");
        return path.toString();
    }

    void stopAll() {
        for (Instance instance : running) host.ownership().release(instance);
        running.clear();
    }
}
