package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.query.Selector;
import com.meekdev.moud.core.value.Value;
import com.meekdev.moud.core.scene.Scene;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;
import com.meekdev.moud.script.bind.Plain;
import com.meekdev.moud.script.bind.Proxies;

public final class TreeMethods {

    private TreeMethods() {}

    public static void install(LuaState state) {
        Proxies.extraMethod(state, "findFirstDescendant", s -> {
            Instance found = first(self(s), s.checkString(2));
            Plain.push(s, found);
            return 1;
        });
        Proxies.extraMethod(state, "descendants", s -> {
            ClassDef<?> def = s.isNoneOrNil(2) ? null : type(s, 2);
            List<Instance> out = new ArrayList<>();
            descendants(self(s), def, out);
            Plain.push(s, out);
            return 1;
        });
        Proxies.extraMethod(state, "childrenOfClass", s -> {
            ClassDef<?> def = type(s, 2);
            List<Instance> out = new ArrayList<>();
            for (Instance child : self(s).children()) {
                if (child.def().isA(def)) out.add(child);
            }
            Plain.push(s, out);
            return 1;
        });
        Proxies.extraMethod(state, "firstAncestorOfClass", s -> {
            ClassDef<?> def = type(s, 2);
            Instance up = self(s).parent();
            while (up != null && !up.def().isA(def)) up = up.parent();
            Plain.push(s, up);
            return 1;
        });
        Proxies.extraMethod(state, "firstAncestor", s -> {
            String name = s.checkString(2);
            Instance up = self(s).parent();
            while (up != null && !up.name().equals(name)) up = up.parent();
            Plain.push(s, up);
            return 1;
        });
        Proxies.extraMethod(state, "isDescendantOf", s -> {
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Instance other)) throw s.error("isDescendantOf wants an instance");
            Instance up = self(s).parent();
            while (up != null && up != other) up = up.parent();
            s.pushBoolean(up != null);
            return 1;
        });
        Proxies.extraMethod(state, "byTag", s -> {
            Instance root = self(s);
            List<Instance> out = new ArrayList<>();
            for (Instance tagged : root.tree().tagged(s.checkString(2))) {
                for (Instance up = tagged.parent(); up != null; up = up.parent()) {
                    if (up == root) {
                        out.add(tagged);
                        break;
                    }
                }
            }
            Plain.push(s, out);
            return 1;
        });
        Proxies.extraMethod(state, "values", s -> {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Instance child : self(s).children()) {
                if (!(child instanceof Value)) continue;
                PropertyDef value = child.def().property("value");
                if (value == null) continue;
                out.put(child.name(), value.type().isBool() ? value.getBool(child)
                        : value.isNumeric() ? value.getNum(child) : value.getObj(child));
            }
            Plain.push(s, out);
            return 1;
        });
        Proxies.extraMethod(state, "clone", s -> {
            Instance original = self(s);
            Instance parent = s.isNoneOrNil(2) ? original.parent() : (Instance) s.toUserDataTagged(2, Proxies.TAG);
            if (parent == null) throw s.error("the root cannot be cloned without somewhere to put it");
            Instance holder = Instances.create(Classes.FOLDER, parent, "clone");
            List<Instance> made = Scene.load(Scene.save(List.of(original)), holder, Proxies.registry());
            Instance copy = made.isEmpty() ? null : made.getFirst();
            if (copy != null) Instances.reparent(copy, parent);
            Instances.destroy(holder);
            Plain.push(s, copy);
            return 1;
        });
        Proxies.extraMethod(state, "query", s -> {
            try {
                Plain.push(s, Selector.parse(s.checkString(2), Proxies.registry()).all(self(s)));
            } catch (IllegalArgumentException wrong) {
                throw s.error("%s", wrong.getMessage());
            }
            return 1;
        });
        Proxies.extraMethod(state, "queryFirst", s -> {
            try {
                List<Instance> found = Selector.parse(s.checkString(2), Proxies.registry()).all(self(s));
                Plain.push(s, found.isEmpty() ? null : found.getFirst());
            } catch (IllegalArgumentException wrong) {
                throw s.error("%s", wrong.getMessage());
            }
            return 1;
        });

        state.pushFunction(LuaFunc.wrap(s -> {
            String name = s.checkString(1);
            if (s.type(2) != LuaType.FUNCTION) throw s.error("a method is a function");
            Proxies.luauMethod(s, name, 2);
            return 0;
        }, "__moud_instance_method"));
        state.setGlobal("__moud_instance_method");
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

    private static void descendants(Instance at, ClassDef<?> def, List<Instance> out) {
        for (Instance child : at.children()) {
            if (def == null || child.def().isA(def)) out.add(child);
            descendants(child, def, out);
        }
    }

    private static ClassDef<?> type(LuaState s, int at) {
        String name = s.checkString(at);
        ClassDef<?> def = Proxies.registry().find(name);
        if (def == null) throw s.error("there is no class called %s", name);
        return def;
    }

    private static Instance self(LuaState s) {
        if (!(s.toUserDataTagged(1, Proxies.TAG) instanceof Instance instance)) throw s.error("not an instance");
        return instance;
    }
}
