package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Values {

    static final int VEC3 = 2;
    static final int COLOR = 3;
    static final int CFRAME = 4;
    static final int QUAT = 5;
    static final int UDIM2 = 10;

    private Values() {}

    public static void install(LuaState state) {
        state.newTable();
        LuaTables.function(state, "vec3", "__index", Values::vec3Index);
        LuaTables.function(state, "vec3", "__add", Values::vec3Add);
        LuaTables.function(state, "vec3", "__sub", Values::vec3Sub);
        LuaTables.function(state, "vec3", "__mul", Values::vec3Mul);
        LuaTables.function(state, "vec3", "__unm", Values::vec3Neg);
        LuaTables.function(state, "vec3", "__eq", Values::vec3Eq);
        LuaTables.function(state, "vec3", "__tostring", Values::vec3Text);
        state.setUserDataMetaTable(VEC3);

        state.newTable();
        LuaTables.function(state, "color", "__index", Values::colorIndex);
        LuaTables.function(state, "color", "__eq", Values::colorEq);
        LuaTables.function(state, "color", "__tostring", Values::colorText);
        state.setUserDataMetaTable(COLOR);

        state.pushFunction(LuaFunc.wrap(Values::newVec3, "vec3"));
        state.setGlobal("vec3");
        state.pushFunction(LuaFunc.wrap(Values::newColor, "color"));
        state.setGlobal("color");

        state.newTable();
        LuaTables.function(state, "cframe", "__index", Values::cframeIndex);
        LuaTables.function(state, "cframe", "__mul", Values::cframeMul);
        LuaTables.function(state, "cframe", "__eq", Values::cframeEq);
        LuaTables.function(state, "cframe", "__tostring", Values::cframeText);
        state.setUserDataMetaTable(CFRAME);

        state.newTable();
        LuaTables.function(state, "quat", "__index", Values::quatIndex);
        LuaTables.function(state, "quat", "__eq", Values::quatEq);
        state.setUserDataMetaTable(QUAT);

        state.newTable();
        LuaTables.function(state, "cframe", "angles", Values::cframeAngles);
        LuaTables.function(state, "cframe", "lookAt", Values::cframeLookAt);
        push(state, CFrame.IDENTITY);
        state.rawSetField(-2, "identity");
        state.newTable();
        state.pushFunction(LuaFunc.wrap(Values::newCFrame, "cframe"));
        state.rawSetField(-2, "__call");
        state.setMetaTable(-2);
        state.setGlobal("cframe");

        state.newTable();
        LuaTables.function(state, "udim2", "__index", Values::udim2Index);
        LuaTables.function(state, "udim2", "__add", s -> {
            push(s, udim2(s, 1).add(udim2(s, 2)));
            return 1;
        });
        LuaTables.function(state, "udim2", "__sub", s -> {
            push(s, udim2(s, 1).sub(udim2(s, 2)));
            return 1;
        });
        LuaTables.function(state, "udim2", "__eq", s -> {
            s.pushBoolean(udim2(s, 1).equals(s.toUserDataTagged(2, UDIM2)));
            return 1;
        });
        LuaTables.function(state, "udim2", "__tostring", s -> {
            UDim2 u = udim2(s, 1);
            s.pushString("udim2(" + u.xScale() + ", " + u.xOffset() + ", " + u.yScale() + ", "
                    + u.yOffset() + ")");
            return 1;
        });
        state.setUserDataMetaTable(UDIM2);

        state.newTable();
        LuaTables.function(state, "udim2", "fromScale", s -> {
            push(s, UDim2.fromScale(s.checkNumber(1), s.checkNumber(2)));
            return 1;
        });
        LuaTables.function(state, "udim2", "fromOffset", s -> {
            push(s, UDim2.fromOffset(s.checkNumber(1), s.checkNumber(2)));
            return 1;
        });
        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            push(s, new UDim2(s.checkNumber(2), s.checkNumber(3), s.checkNumber(4), s.checkNumber(5)));
            return 1;
        }, "udim2"));
        state.rawSetField(-2, "__call");
        state.setMetaTable(-2);
        state.setGlobal("udim2");

        methods(state, VEC3_METHODS, Map.of(
                "distance", st -> number(st, vec3(st, 1).distance(vec3(st, 2))),
                "distanceSq", st -> number(st, vec3(st, 1).sub(vec3(st, 2)).lengthSq()),
                "dot", st -> number(st, vec3(st, 1).dot(vec3(st, 2))),
                "cross", st -> one(st, vec3(st, 1).cross(vec3(st, 2))),
                "lerp", st -> one(st, vec3(st, 1).lerp(vec3(st, 2), st.checkNumber(3))),
                "angleTo", st -> number(st, angle(vec3(st, 1), vec3(st, 2))),
                "flat", st -> one(st, new Vector3(vec3(st, 1).x(), 0, vec3(st, 1).z())),
                "clampMagnitude", st -> {
                    Vector3 v = vec3(st, 1);
                    double max = st.checkNumber(2);
                    return one(st, v.lengthSq() > max * max ? v.normalize().mul(max) : v);
                },
                "abs", st -> one(st, new Vector3(Math.abs(vec3(st, 1).x()), Math.abs(vec3(st, 1).y()), Math.abs(vec3(st, 1).z()))),
                "floor", st -> one(st, new Vector3(Math.floor(vec3(st, 1).x()), Math.floor(vec3(st, 1).y()), Math.floor(vec3(st, 1).z())))));
        methods(state, CFRAME_METHODS, Map.of(
                "inverse", st -> one(st, cframe(st, 1).inverse()),
                "lerp", st -> one(st, cframe(st, 1).lerp(cframe(st, 2), st.checkNumber(3))),
                "toObjectSpace", st -> one(st, cframe(st, 1).inverse().mul(cframe(st, 2))),
                "toWorldSpace", st -> one(st, cframe(st, 1).mul(cframe(st, 2))),
                "pointToObjectSpace", st -> one(st, cframe(st, 1).pointToObject(vec3(st, 2))),
                "pointToWorldSpace", st -> one(st, cframe(st, 1).pointToWorld(vec3(st, 2))),
                "vectorToObjectSpace", st -> one(st, cframe(st, 1).vectorToObject(vec3(st, 2))),
                "vectorToWorldSpace", st -> one(st, cframe(st, 1).vectorToWorld(vec3(st, 2))),
                "lookAt", st -> one(st, new CFrame(cframe(st, 1).position(),
                        Quat.lookAt(vec3(st, 2).sub(cframe(st, 1).position()), Vector3.UP)))));
        methods(state, QUAT_METHODS, Map.of(
                "slerp", st -> one(st, quat(st, 1).slerp(quat(st, 2), st.checkNumber(3))),
                "inverse", st -> one(st, quat(st, 1).inverse()),
                "rotate", st -> one(st, quat(st, 1).rotate(vec3(st, 2))),
                "mul", st -> one(st, quat(st, 1).mul(quat(st, 2)))));

        state.newTable();
        push(state, Quat.IDENTITY);
        state.rawSetField(-2, "identity");
        LuaTables.function(state, "quat", "axisAngle", st -> one(st, Quat.axisAngle(vec3(st, 1).normalize(), st.checkNumber(2))));
        LuaTables.function(state, "quat", "euler", st -> one(st, Quat.euler(st.checkNumber(1), st.checkNumber(2), st.checkNumber(3))));
        LuaTables.function(state, "quat", "lookAt", st -> one(st, Quat.lookAt(vec3(st, 1), st.isNoneOrNil(2) ? Vector3.UP : vec3(st, 2))));
        LuaTables.function(state, "quat", "fromTo", st -> one(st, fromTo(vec3(st, 1), vec3(st, 2))));
        state.setGlobal("quat");
    }

    private static final String VEC3_METHODS = "moud.vec3.methods";
    private static final String CFRAME_METHODS = "moud.cframe.methods";
    private static final String QUAT_METHODS = "moud.quat.methods";

    private static void methods(LuaState state, String key, Map<String, ToIntFunction<LuaState>> methods) {
        state.newTable();
        for (Map.Entry<String, ToIntFunction<LuaState>> entry : methods.entrySet()) {
            state.pushFunction(LuaFunc.wrap(entry.getValue()::applyAsInt, key + ":" + entry.getKey()));
            state.rawSetField(-2, entry.getKey());
        }
        state.rawSetField(LuaState.REGISTRY_INDEX, key);
    }

    private static boolean method(LuaState state, String table, String name) {
        state.rawGetField(LuaState.REGISTRY_INDEX, table);
        if (state.rawGetField(-1, name) == LuaType.NIL) {
            state.pop(2);
            return false;
        }
        state.remove(-2);
        return true;
    }

    private static int number(LuaState state, double value) {
        state.pushNumber(value);
        return 1;
    }

    private static int one(LuaState state, Object value) {
        push(state, value);
        return 1;
    }

    private static double angle(Vector3 a, Vector3 b) {
        double lengths = a.length() * b.length();
        return lengths < 1e-12 ? 0 : Math.acos(Math.clamp(a.dot(b) / lengths, -1, 1));
    }

    static Quat fromTo(Vector3 from, Vector3 to) {
        Vector3 a = from.normalize();
        Vector3 b = to.normalize();
        double dot = a.dot(b);
        if (dot > 1 - 1e-9) return Quat.IDENTITY;
        if (dot < -1 + 1e-9) {
            Vector3 axis = Vector3.RIGHT.cross(a);
            if (axis.lengthSq() < 1e-9) axis = Vector3.UP.cross(a);
            return Quat.axisAngle(axis.normalize(), Math.PI);
        }
        Vector3 axis = a.cross(b);
        return new Quat(axis.x(), axis.y(), axis.z(), 1 + dot).normalize();
    }

    static Quat quat(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, QUAT);
        if (value == null) throw state.error("expected a quat");
        return (Quat) value;
    }

    public static void push(LuaState state, UDim2 u) {
        state.newUserDataTaggedWithMetatable(u, UDIM2);
    }

    public static UDim2 udim2(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, UDIM2);
        if (value == null) throw state.error("expected a udim2");
        return (UDim2) value;
    }

    private static int udim2Index(LuaState state) {
        UDim2 u = udim2(state, 1);
        switch (state.checkString(2)) {
            case "xScale" -> state.pushNumber(u.xScale());
            case "xOffset" -> state.pushNumber(u.xOffset());
            case "yScale" -> state.pushNumber(u.yScale());
            case "yOffset" -> state.pushNumber(u.yOffset());
            default -> throw state.error("udim2 has no member '%s'", state.checkString(2));
        }
        return 1;
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

    public static void push(LuaState state, Vector3 v) {
        state.newUserDataTaggedWithMetatable(v, VEC3);
    }

    public static void push(LuaState state, Color c) {
        state.newUserDataTaggedWithMetatable(c, COLOR);
    }

    public static Vector3 vec3(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, VEC3);
        if (value == null) throw state.error("expected a vec3");
        return (Vector3) value;
    }

    public static Color color(LuaState state, int index) {
        Object value = state.toUserDataTagged(index, COLOR);
        if (value == null) throw state.error("expected a color");
        return (Color) value;
    }

    public static Object value(LuaState state, int index) {
        Object v = state.toUserDataTagged(index, VEC3);
        if (v != null) return v;
        v = state.toUserDataTagged(index, COLOR);
        if (v != null) return v;
        v = state.toUserDataTagged(index, CFRAME);
        if (v != null) return v;
        v = state.toUserDataTagged(index, UDIM2);
        if (v != null) return v;
        return state.toUserDataTagged(index, QUAT);
    }

    public static boolean push(LuaState state, Object value) {
        switch (value) {
            case Vector3 v -> push(state, v);
            case Color c -> push(state, c);
            case CFrame c -> push(state, c);
            case Quat q -> push(state, q);
            case UDim2 u -> push(state, u);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int newVec3(LuaState state) {
        push(state, new Vector3(state.checkNumber(1), state.checkNumber(2), state.checkNumber(3)));
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
        Vector3 v = vec3(state, 1);
        switch (state.checkString(2)) {
            case "x" -> state.pushNumber(v.x());
            case "y" -> state.pushNumber(v.y());
            case "z" -> state.pushNumber(v.z());
            case "magnitude" -> state.pushNumber(v.length());
            case "unit" -> push(state, v.normalize());
            default -> {
                if (!method(state, VEC3_METHODS, state.checkString(2))) throw state.error("vec3 has no member '%s'", state.checkString(2));
            }
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
        Vector3 v = vec3(state, 1);
        state.pushString("vec3(" + v.x() + ", " + v.y() + ", " + v.z() + ")");
        return 1;
    }

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
            default -> {
                if (!method(state, CFRAME_METHODS, state.checkString(2))) throw state.error("cframe has no member '%s'", state.checkString(2));
            }
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
            default -> {
                if (!method(state, QUAT_METHODS, state.checkString(2))) throw state.error("quat has no member '%s'", state.checkString(2));
            }
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
