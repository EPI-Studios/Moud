package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// values are immutable userdata, which is what stops a place aliasing a part's size and mutating it
public final class Values {

    static final int VEC3 = 2;
    static final int COLOR = 3;

    private Values() {}

    public static void install(LuaState state) {
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::vec3Index, "vec3.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Values::vec3Add, "vec3.__add"));
        state.rawSetField(-2, "__add");
        state.pushFunction(LuaFunc.wrap(Values::vec3Sub, "vec3.__sub"));
        state.rawSetField(-2, "__sub");
        state.pushFunction(LuaFunc.wrap(Values::vec3Mul, "vec3.__mul"));
        state.rawSetField(-2, "__mul");
        state.pushFunction(LuaFunc.wrap(Values::vec3Neg, "vec3.__unm"));
        state.rawSetField(-2, "__unm");
        state.pushFunction(LuaFunc.wrap(Values::vec3Eq, "vec3.__eq"));
        state.rawSetField(-2, "__eq");
        state.pushFunction(LuaFunc.wrap(Values::vec3Text, "vec3.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.setUserDataMetaTable(VEC3);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::colorIndex, "color.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Values::colorEq, "color.__eq"));
        state.rawSetField(-2, "__eq");
        state.pushFunction(LuaFunc.wrap(Values::colorText, "color.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.setUserDataMetaTable(COLOR);

        state.pushFunction(LuaFunc.wrap(Values::newVec3, "vec3"));
        state.setGlobal("vec3");
        state.pushFunction(LuaFunc.wrap(Values::newColor, "color"));
        state.setGlobal("color");
    }

    public static void push(LuaState state, Vec3 v) {
        state.newUserDataTaggedWithMetatable(v, VEC3);
    }

    public static void push(LuaState state, Color c) {
        state.newUserDataTaggedWithMetatable(c, COLOR);
    }

    public static Vec3 vec3(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, VEC3);
        if (value == null) throw state.error("expected a vec3");
        return (Vec3) value;
    }

    public static Color color(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, COLOR);
        if (value == null) throw state.error("expected a color");
        return (Color) value;
    }

    private static int newVec3(LuaState state) {
        push(state, new Vec3(state.checkNumber(1), state.checkNumber(2), state.checkNumber(3)));
        return 1;
    }

    private static int newColor(LuaState state) {
        float r = (float) state.checkNumber(1);
        float g = (float) state.checkNumber(2);
        float b = (float) state.checkNumber(3);
        float a = state.isNoneOrNil(4) ? 1.0f : (float) state.checkNumber(4);
        push(state, new Color(r, g, b, a));
        return 1;
    }

    private static int vec3Index(LuaState state) {
        Vec3 v = vec3(state, 1);
        switch (state.checkString(2)) {
            case "x" -> state.pushNumber(v.x());
            case "y" -> state.pushNumber(v.y());
            case "z" -> state.pushNumber(v.z());
            case "magnitude" -> state.pushNumber(v.length());
            case "unit" -> push(state, v.normalize());
            default -> throw state.error("vec3 has no member '%s'", state.checkString(2));
        }
        return 1;
    }

    private static int vec3Add(LuaState state) {
        push(state, vec3(state, 1).add(vec3(state, 2)));
        return 1;
    }

    private static int vec3Sub(LuaState state) {
        push(state, vec3(state, 1).sub(vec3(state, 2)));
        return 1;
    }

    // luau hands __mul its operands in either order, so the scalar can be on either side
    private static int vec3Mul(LuaState state) {
        if (state.isNumber(2)) {
            push(state, vec3(state, 1).mul(state.toNumber(2)));
        } else if (state.isNumber(1)) {
            push(state, vec3(state, 2).mul(state.toNumber(1)));
        } else {
            push(state, vec3(state, 1).mul(vec3(state, 2)));
        }
        return 1;
    }

    private static int vec3Neg(LuaState state) {
        push(state, vec3(state, 1).neg());
        return 1;
    }

    private static int vec3Eq(LuaState state) {
        state.pushBoolean(vec3(state, 1).equals(vec3(state, 2)));
        return 1;
    }

    private static int vec3Text(LuaState state) {
        Vec3 v = vec3(state, 1);
        state.pushString("vec3(" + v.x() + ", " + v.y() + ", " + v.z() + ")");
        return 1;
    }

    private static int colorIndex(LuaState state) {
        Color c = color(state, 1);
        switch (state.checkString(2)) {
            case "r" -> state.pushNumber(c.r());
            case "g" -> state.pushNumber(c.g());
            case "b" -> state.pushNumber(c.b());
            case "a" -> state.pushNumber(c.a());
            default -> throw state.error("color has no member '%s'", state.checkString(2));
        }
        return 1;
    }

    private static int colorEq(LuaState state) {
        state.pushBoolean(color(state, 1).equals(color(state, 2)));
        return 1;
    }

    private static int colorText(LuaState state) {
        Color c = color(state, 1);
        state.pushString("color(" + c.r() + ", " + c.g() + ", " + c.b() + ", " + c.a() + ")");
        return 1;
    }
}
