package com.meekdev.moud.script.host.render;

import com.meekdev.moud.script.api.ShaderRef;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShaderLibrary {

    private static final Builtin REMOVE = new Builtin("ShaderPatch:remove", a -> {
        a.self(Handle.class).remove();
        return null;
    });

    private static final class Handle implements HostObject {
        private final ShaderRef.Patch patch;
        private final Map<String, String> uniforms;
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final List<Handle> open;
        private boolean removed;

        Handle(ShaderRef.Patch patch, Map<String, String> uniforms, List<Handle> open) {
            this.patch = patch;
            this.uniforms = uniforms;
            this.open = open;
        }

        void remove() {
            if (removed) return;
            removed = true;
            open.remove(this);
            patch.remove();
        }

        @Override
        public String typeName() {
            return "ShaderPatch";
        }

        @Override
        public Object get(String key) {
            if (key.equals("remove")) return REMOVE;
            if (!uniforms.containsKey(key)) throw new HostError("this patch has no uniform '%s'", key);
            return values.get(key);
        }

        @Override
        public void set(String key, Object value) {
            if (!uniforms.containsKey(key)) throw new HostError("this patch has no uniform '%s', declare it in uniforms", key);
            if (value == null) throw new HostError("uniform %s can not be nil", key);
            values.put(key, value);
            patch.set(key, value);
        }
    }

    private ShaderLibrary() {}

    public static void install(Host host) {
        ShaderRef shaders = host.shaders();
        if (shaders == null) return;
        List<Handle> open = new ArrayList<>();
        host.onClose(() -> new ArrayList<>(open).forEach(Handle::remove));
        host.api().declare(new Members("ShaderPatch").declareMethod("remove", "() -> ()").decl());
        Members members = new Members("Shaders")
                .function("patch", "(targets: string | { string }, spec: { uniforms: { [string]: string }?, vertex: { [string]: any }?, "
                        + "fragment: { [string]: any }? }) -> ShaderPatch & { [string]: any }", a -> {
                    List<String> targets = new ArrayList<>();
                    if (a.get(0) instanceof String one) targets.add(one);
                    else for (Object target : a.list(0)) targets.add(String.valueOf(target));
                    if (targets.isEmpty()) throw a.error("name at least one shader to patch, like \"minecraft:core/terrain\"");
                    Map<String, Object> spec = a.map(1);
                    Map<String, String> uniforms = new ConcurrentHashMap<>();
                    if (spec.get("uniforms") instanceof Map<?, ?> declared) {
                        declared.forEach((name, type) -> uniforms.put(String.valueOf(name), String.valueOf(type)));
                    }
                    ShaderRef.Patch patch = shaders.patch(targets, spec, problem -> host.error("shaders.patch", new HostError(problem)));
                    Handle handle = new Handle(patch, uniforms, open);
                    open.add(handle);
                    return handle;
                });
        host.global("shaders", "Shaders", members);
        host.declare(members);
    }
}
