package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// values are immutable userdata, which is what stops a place aliasing a part's size and mutating it
public final class Values {

    static final int VEC3 = 2;
    static final int COLOR = 3;
    static final int CFRAME = 4;
    static final int QUAT = 5;

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

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::cframeIndex, "cframe.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Values::cframeMul, "cframe.__mul"));
        state.rawSetField(-2, "__mul");
        state.pushFunction(LuaFunc.wrap(Values::cframeEq, "cframe.__eq"));
        state.rawSetField(-2, "__eq");
        state.pushFunction(LuaFunc.wrap(Values::cframeText, "cframe.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.setUserDataMetaTable(CFRAME);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::quatIndex, "quat.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Values::quatEq, "quat.__eq"));
        state.rawSetField(-2, "__eq");
        state.setUserDataMetaTable(QUAT);

        // cframe is a callable table so cframe(...), cframe.angles and cframe.identity all live
        // under one name, which is the surface design 8.3 asks for
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::cframeAngles, "cframe.angles"));
        state.rawSetField(-2, "angles");
        state.pushFunction(LuaFunc.wrap(Values::cframeLookAt, "cframe.lookAt"));
        state.rawSetField(-2, "lookAt");
        push(state, CFrame.IDENTITY);
        state.rawSetField(-2, "identity");
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::newCFrame, "cframe"));
        state.rawSetField(-2, "__call");
        state.setMetaTable(-2);
        state.setGlobal("cframe");
    }

    public static void push(LuaState state, CFrame c) {
        state.newUserDataTaggedWithMetatable(c, CFRAME);
    }

    public static void push(LuaState state, Quat q) {
        state.newUserDataTaggedWithMetatable(q, QUAT);
    }

    public static CFrame cframe(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, CFRAME);
        if (value == null) throw state.error("expected a cframe");
        return (CFrame) value;
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

    // the value types as plain objects, for anything that has to carry them outside a vm
    public static Object value(LuaState state, int index) {
        Object v = state.toUserDataTagged(index, VEC3);
        if (v != null) return v;
        v = state.toUserDataTagged(index, COLOR);
        if (v != null) return v;
        v = state.toUserDataTagged(index, CFRAME);
        if (v != null) return v;
        return state.toUserDataTagged(index, QUAT);
    }

    public static boolean push(LuaState state, Object value) {
        switch (value) {
            case Vec3 v -> push(state, v);
            case Color c -> push(state, c);
            case CFrame c -> push(state, c);
            case Quat q -> push(state, q);
            default -> {
                return false;
            }
        }
        return true;
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

    // arg 1 is the cframe table itself, because this is __call
    private static int newCFrame(LuaState state) {
        if (state.isNumber(2)) {
            push(state, CFrame.at(state.checkNumber(2), state.checkNumber(3), state.checkNumber(4)));
        } else if (state.isNoneOrNil(3)) {
            push(state, CFrame.at(vec3(state, 2)));
        } else {
            push(state, CFrame.lookAt(vec3(state, 2), vec3(state, 3)));
        }
        return 1;
    }

    private static int cframeAngles(LuaState state) {
        push(state, CFrame.angles(state.checkNumber(1), state.checkNumber(2), state.checkNumber(3)));
        return 1;
    }

    private static int cframeLookAt(LuaState state) {
        push(state, CFrame.lookAt(vec3(state, 1), vec3(state, 2)));
        return 1;
    }

    private static int cframeIndex(LuaState state) {
        CFrame c = cframe(state, 1);
        switch (state.checkString(2)) {
            case "position" -> push(state, c.position());
            case "rotation" -> push(state, c.rotation());
            case "lookVector" -> push(state, c.lookVector());
            case "rightVector" -> push(state, c.rightVector());
            case "upVector" -> push(state, c.upVector());
            default -> throw state.error("cframe has no member '%s'", state.checkString(2));
        }
        return 1;
    }

    private static int cframeMul(LuaState state) {
        push(state, cframe(state, 1).mul(cframe(state, 2)));
        return 1;
    }

    private static int cframeEq(LuaState state) {
        state.pushBoolean(cframe(state, 1).equals(cframe(state, 2)));
        return 1;
    }

    private static int cframeText(LuaState state) {
        CFrame c = cframe(state, 1);
        state.pushString("cframe(" + c.position().x() + ", " + c.position().y() + ", " + c.position().z() + ")");
        return 1;
    }

    private static int quatIndex(LuaState state) {
        Object value = state.toUserDataTagged(1, QUAT);
        if (value == null) throw state.error("expected a quat");
        Quat q = (Quat) value;
        switch (state.checkString(2)) {
            case "x" -> state.pushNumber(q.x());
            case "y" -> state.pushNumber(q.y());
            case "z" -> state.pushNumber(q.z());
            case "w" -> state.pushNumber(q.w());
            default -> throw state.error("quat has no member '%s'", state.checkString(2));
        }
        return 1;
    }

    private static int quatEq(LuaState state) {
        state.pushBoolean(state.toUserDataTagged(1, QUAT).equals(state.toUserDataTagged(2, QUAT)));
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
