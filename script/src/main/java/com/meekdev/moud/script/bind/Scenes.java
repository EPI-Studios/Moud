package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.script.api.FileRef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// the scene global: branches of the tree to and from scene files
public final class Scenes {

    private Scenes() {}

    public static void install(LuaState state, Instance world, ClassRegistry classes, FileRef files) {
        state.newTable();
        function(state, "load", s -> {
            String path = s.checkString(1);
            String text = files.read(path);
            if (text == null) throw s.error("there is no scene at %s", path);
            return push(s, decode(s, text, parent(s, 2, world), classes, path));
        });
        function(state, "decode", s -> push(s, decode(s, s.checkString(1), parent(s, 2, world), classes, "the text")));
        function(state, "save", s -> {
            String path = s.checkString(2);
            if (!path.endsWith(".scene")) throw s.error("a scene is saved to a .scene file, not %s", path);
            try {
                files.write(path, Scene.save(roots(s, 1)));
            } catch (IllegalArgumentException | IllegalStateException wrong) {
                throw s.error("%s", wrong.getMessage());
            }
            return 0;
        });
        function(state, "encode", s -> {
            s.pushString(Scene.save(roots(s, 1)));
            return 1;
        });
        state.setGlobal("scene");
    }

    private static List<Instance> decode(LuaState state, String text, Instance parent, ClassRegistry classes, String from) {
        try {
            return Scene.load(text, parent, classes);
        } catch (IllegalArgumentException wrong) {
            throw state.error("%s: %s", from, wrong.getMessage());
        }
    }

    private static Instance parent(LuaState state, int at, Instance world) {
        if (state.isNoneOrNil(at)) return world;
        if (!(state.toUserDataTagged(at, Proxies.TAG) instanceof Instance parent)) {
            throw state.error("a scene is loaded under an instance");
        }
        return parent;
    }

    // one instance, or a list of them
    private static List<Instance> roots(LuaState state, int at) {
        if (state.toUserDataTagged(at, Proxies.TAG) instanceof Instance one) return List.of(one);
        if (state.type(at) != LuaType.TABLE) throw state.error("save an instance or a list of instances");
        List<Instance> out = new ArrayList<>();
        int length = state.len(at);
        for (int n = 1; n <= length; n++) {
            state.rawGetI(at, n);
            if (!(state.toUserDataTagged(-1, Proxies.TAG) instanceof Instance instance)) {
                throw state.error("save an instance or a list of instances");
            }
            out.add(instance);
            state.pop(1);
        }
        return out;
    }

    private static int push(LuaState state, List<Instance> instances) {
        state.createTable(instances.size(), 0);
        for (int n = 0; n < instances.size(); n++) {
            Proxies.push(state, instances.get(n));
            state.rawSetI(-2, n + 1);
        }
        return 1;
    }

    private static void function(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "scene." + name));
        state.rawSetField(-2, name);
    }
}
