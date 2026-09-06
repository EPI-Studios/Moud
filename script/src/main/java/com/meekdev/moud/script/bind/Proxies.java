package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Proxies {

    static final int TAG = 1;

    // one proxy per instance so identity holds in luau, weak valued so a dead one can go
    private static final String CACHE = "moud.instances";

    private Proxies() {}

    public static void install(LuaState state) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Proxies::index, "Instance.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Proxies::newIndex, "Instance.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.pushFunction(LuaFunc.wrap(Proxies::name, "Instance.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.setUserDataMetaTable(TAG);

        state.newTable();
        state.newTable();
        state.pushString("v");
        state.rawSetField(-2, "__mode");
        state.setMetaTable(-2);
        state.rawSetField(LuaState.REGISTRY_INDEX, CACHE);
    }

    public static void push(LuaState state, Instance instance) {
        state.rawGetField(LuaState.REGISTRY_INDEX, CACHE);
        if (state.rawGetI(-1, instance.id()) != LuaType.NIL) {
            state.remove(-2);
            return;
        }
        state.pop(1);
        state.newUserDataTaggedWithMetatable(instance, TAG);
        state.pushValue(-1);
        state.rawSetI(-3, instance.id());
        state.remove(-2);
    }

    private static Instance self(LuaState state) {
        Instance instance = (Instance) state.toUserDataTagged(1, TAG);
        if (instance == null) throw state.error("not an instance");
        // a destroyed instance is a bug in the place, not something to paper over
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
            default -> { }
        }

        // cframe is the local frame, matching the field. the composed one is its own name so
        // neither reading is a silent surprise
        if (key.equals("worldCframe") && instance instanceof Spatial) {
            Values.push(state, Transforms.world(instance));
            return 1;
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
        throw state.error("%s has no member '%s'", instance.def().name(), key);
    }

    private static int newIndex(LuaState state) {
        Instance instance = self(state);
        String key = state.checkString(2);

        if (key.equals("name")) {
            Instances.rename(instance, state.checkString(3));
            return 0;
        }

        if (key.equals("worldCframe") && instance instanceof Spatial) {
            PropertyDef local = instance.def().property("cframe");
            Instances.setObj(instance, local, Transforms.localFor(instance, Values.cframe(state, 3)));
            return 0;
        }

        PropertyDef property = instance.def().property(key);
        if (property == null) throw state.error("%s has no property '%s'", instance.def().name(), key);
        write(state, instance, property);
        return 0;
    }

    private static int name(LuaState state) {
        state.pushString(self(state).name());
        return 1;
    }

    private static void read(LuaState state, Instance instance, PropertyDef property) {
        switch (property.type()) {
            case BOOL -> state.pushBoolean(property.getBool(instance));
            case INT, NUM -> state.pushNumber(property.getNum(instance));
            case STRING -> state.pushString((String) property.getObj(instance));
            case VEC3 -> Values.push(state, (Vec3) property.getObj(instance));
            case COLOR -> Values.push(state, (Color) property.getObj(instance));
            case CFRAME -> Values.push(state, (CFrame) property.getObj(instance));
            default -> throw state.error("%s is not a value luau can read yet", property.name());
        }
    }

    private static void write(LuaState state, Instance instance, PropertyDef property) {
        switch (property.type()) {
            case BOOL -> Instances.setBool(instance, property, state.toBoolean(3));
            case INT, NUM -> Instances.setNum(instance, property, state.checkNumber(3));
            case STRING -> Instances.setObj(instance, property, state.checkString(3));
            case VEC3 -> Instances.setObj(instance, property, Values.vec3(state, 3));
            case COLOR -> Instances.setObj(instance, property, Values.color(state, 3));
            case CFRAME -> Instances.setObj(instance, property, Values.cframe(state, 3));
            default -> throw state.error("%s is not a value luau can write yet", property.name());
        }
    }
}
