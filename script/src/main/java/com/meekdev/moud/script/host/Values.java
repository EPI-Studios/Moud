package com.meekdev.moud.script.host;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.List;
import java.util.Map;

public final class Values {

    private static final Members VEC3 = new Members("Vector3")
            .method("add", "(other: Vector3) -> Vector3", a -> self(a, Vector3.class).add(a.vector(1)))
            .method("sub", "(other: Vector3) -> Vector3", a -> self(a, Vector3.class).sub(a.vector(1)))
            .method("mul", "(by: number | Vector3) -> Vector3", a -> operate(Host.Op.MUL, self(a, Vector3.class), a.get(1)))
            .method("div", "(by: number | Vector3) -> Vector3", a -> operate(Host.Op.DIV, self(a, Vector3.class), a.get(1)))
            .method("neg", "() -> Vector3", a -> self(a, Vector3.class).neg())
            .method("distance", "(other: Vector3) -> number", a -> self(a, Vector3.class).distance(a.vector(1)))
            .method("distanceSq", "(other: Vector3) -> number", a -> self(a, Vector3.class).sub(a.vector(1)).lengthSq())
            .method("dot", "(other: Vector3) -> number", a -> self(a, Vector3.class).dot(a.vector(1)))
            .method("cross", "(other: Vector3) -> Vector3", a -> self(a, Vector3.class).cross(a.vector(1)))
            .method("lerp", "(goal: Vector3, alpha: number) -> Vector3", a -> self(a, Vector3.class).lerp(a.vector(1), a.number(2)))
            .method("angleTo", "(other: Vector3) -> number", a -> angle(self(a, Vector3.class), a.vector(1)))
            .method("flat", "() -> Vector3", a -> {
                Vector3 v = self(a, Vector3.class);
                return new Vector3(v.x(), 0, v.z());
            })
            .method("clampMagnitude", "(max: number) -> Vector3", a -> {
                Vector3 v = self(a, Vector3.class);
                double max = a.number(1);
                return v.lengthSq() > max * max ? v.normalize().mul(max) : v;
            })
            .method("abs", "() -> Vector3", a -> {
                Vector3 v = self(a, Vector3.class);
                return new Vector3(Math.abs(v.x()), Math.abs(v.y()), Math.abs(v.z()));
            })
            .method("floor", "() -> Vector3", a -> {
                Vector3 v = self(a, Vector3.class);
                return new Vector3(Math.floor(v.x()), Math.floor(v.y()), Math.floor(v.z()));
            })
            .declare("x", "number")
            .declare("y", "number")
            .declare("z", "number")
            .declare("magnitude", "number")
            .declare("unit", "Vector3");

    private static final Members CFRAME = new Members("CFrame")
            .method("mul", "(other: CFrame) -> CFrame", a -> self(a, CFrame.class).mul(a.cframe(1)))
            .method("inverse", "() -> CFrame", a -> self(a, CFrame.class).inverse())
            .method("lerp", "(goal: CFrame, alpha: number) -> CFrame", a -> self(a, CFrame.class).lerp(a.cframe(1), a.number(2)))
            .method("toObjectSpace", "(other: CFrame) -> CFrame", a -> self(a, CFrame.class).inverse().mul(a.cframe(1)))
            .method("toWorldSpace", "(other: CFrame) -> CFrame", a -> self(a, CFrame.class).mul(a.cframe(1)))
            .method("pointToObjectSpace", "(point: Vector3) -> Vector3", a -> self(a, CFrame.class).pointToObject(a.vector(1)))
            .method("pointToWorldSpace", "(point: Vector3) -> Vector3", a -> self(a, CFrame.class).pointToWorld(a.vector(1)))
            .method("vectorToObjectSpace", "(vector: Vector3) -> Vector3", a -> self(a, CFrame.class).vectorToObject(a.vector(1)))
            .method("vectorToWorldSpace", "(vector: Vector3) -> Vector3", a -> self(a, CFrame.class).vectorToWorld(a.vector(1)))
            .method("lookAt", "(target: Vector3) -> CFrame", a -> {
                CFrame c = self(a, CFrame.class);
                return new CFrame(c.position(), Quat.lookAt(a.vector(1).sub(c.position()), Vector3.UP));
            })
            .declare("position", "Vector3")
            .declare("rotation", "Quat")
            .declare("lookVector", "Vector3")
            .declare("rightVector", "Vector3")
            .declare("upVector", "Vector3");

    private static final Members QUAT = new Members("Quat")
            .method("slerp", "(goal: Quat, alpha: number) -> Quat", a -> self(a, Quat.class).slerp(a.quat(1), a.number(2)))
            .method("inverse", "() -> Quat", a -> self(a, Quat.class).inverse())
            .method("rotate", "(vector: Vector3) -> Vector3", a -> self(a, Quat.class).rotate(a.vector(1)))
            .method("mul", "(other: Quat) -> Quat", a -> self(a, Quat.class).mul(a.quat(1)))
            .declare("x", "number")
            .declare("y", "number")
            .declare("z", "number")
            .declare("w", "number");

    private Values() {}

    private static <T> T self(Args args, Class<T> type) {
        return args.self(type);
    }

    static void install(Host host) {
        host.api().declare(VEC3.decl());
        host.api().declare(CFRAME.decl());
        host.api().declare(QUAT.decl());
        host.api().declare(new Api.Decl("Color", null, List.of(
                new Api.Member("r", Api.Kind.FIELD, "number"),
                new Api.Member("g", Api.Kind.FIELD, "number"),
                new Api.Member("b", Api.Kind.FIELD, "number"),
                new Api.Member("a", Api.Kind.FIELD, "number"))));
        host.api().declare(new Api.Decl("UDim2", null, List.of(
                new Api.Member("xScale", Api.Kind.FIELD, "number"),
                new Api.Member("xOffset", Api.Kind.FIELD, "number"),
                new Api.Member("yScale", Api.Kind.FIELD, "number"),
                new Api.Member("yOffset", Api.Kind.FIELD, "number"))));

        host.global("vec3", "(x: number, y: number, z: number) -> Vector3",
                new Builtin("vec3", a -> new Vector3(a.number(0), a.number(1), a.number(2))));
        host.global("color", "(r: number, g: number, b: number, a: number?) -> Color",
                new Builtin("color", a -> new Color((float) a.number(0), (float) a.number(1), (float) a.number(2),
                        (float) a.number(3, 1))));

        Members cframes = new Members("CFrameConstructors")
                .function("new", "(x: number | Vector3, y: (number | Vector3)?, z: number?) -> CFrame", Values::newCFrame)
                .function("angles", "(x: number, y: number, z: number) -> CFrame",
                        a -> CFrame.angles(a.number(0), a.number(1), a.number(2)))
                .function("lookAt", "(from: Vector3, to: Vector3) -> CFrame", a -> CFrame.lookAt(a.vector(0), a.vector(1)))
                .value("identity", "CFrame", CFrame.IDENTITY);
        host.global("cframe", "CFrameConstructors", callable(cframes, Values::newCFrame));
        host.declare(cframes);

        Members quats = new Members("QuatConstructors")
                .function("axisAngle", "(axis: Vector3, angle: number) -> Quat",
                        a -> Quat.axisAngle(a.vector(0).normalize(), a.number(1)))
                .function("euler", "(x: number, y: number, z: number) -> Quat", a -> Quat.euler(a.number(0), a.number(1), a.number(2)))
                .function("lookAt", "(direction: Vector3, up: Vector3?) -> Quat", a -> Quat.lookAt(a.vector(0), a.vector(1, Vector3.UP)))
                .function("fromTo", "(from: Vector3, to: Vector3) -> Quat", a -> fromTo(a.vector(0), a.vector(1)))
                .value("identity", "Quat", Quat.IDENTITY);
        host.global("quat", "QuatConstructors", quats);
        host.declare(quats);

        Members udims = new Members("UDim2Constructors")
                .function("new", "(xScale: number, xOffset: number, yScale: number, yOffset: number) -> UDim2", Values::newUDim2)
                .function("fromScale", "(x: number, y: number) -> UDim2", a -> UDim2.fromScale(a.number(0), a.number(1)))
                .function("fromOffset", "(x: number, y: number) -> UDim2", a -> UDim2.fromOffset(a.number(0), a.number(1)));
        host.global("udim2", "UDim2Constructors", callable(udims, Values::newUDim2));
        host.declare(udims);
    }

    private static Object newCFrame(Args a) {
        if (a.get(0) instanceof Number) return CFrame.at(a.number(0), a.number(1), a.number(2));
        if (!a.has(1)) return CFrame.at(a.vector(0));
        return CFrame.lookAt(a.vector(0), a.vector(1));
    }

    private static Object newUDim2(Args a) {
        return new UDim2(a.number(0), a.number(1), a.number(2), a.number(3));
    }

    private static Invocable callable(Members members, HostFunction body) {
        return new Invocable() {
            @Override
            public Object invoke(Args args) {
                return body.call(args);
            }

            @Override
            public String typeName() {
                return members.typeName();
            }

            @Override
            public Object get(String key) {
                return members.get(key);
            }
        };
    }

    static Object get(Vector3 v, String key) {
        return switch (key) {
            case "x" -> v.x();
            case "y" -> v.y();
            case "z" -> v.z();
            case "magnitude" -> v.length();
            case "unit" -> v.normalize();
            default -> VEC3.get(key);
        };
    }

    static Object get(CFrame c, String key) {
        return switch (key) {
            case "position" -> c.position();
            case "rotation" -> c.rotation();
            case "lookVector" -> c.lookVector();
            case "rightVector" -> c.rightVector();
            case "upVector" -> c.upVector();
            default -> CFRAME.get(key);
        };
    }

    static Object get(Quat q, String key) {
        return switch (key) {
            case "x" -> q.x();
            case "y" -> q.y();
            case "z" -> q.z();
            case "w" -> q.w();
            default -> QUAT.get(key);
        };
    }

    static Object get(Color c, String key) {
        return switch (key) {
            case "r" -> (double) c.r();
            case "g" -> (double) c.g();
            case "b" -> (double) c.b();
            case "a" -> (double) c.a();
            default -> throw new HostError("color has no member '%s'", key);
        };
    }

    static Object get(UDim2 u, String key) {
        return switch (key) {
            case "xScale" -> u.xScale();
            case "xOffset" -> u.xOffset();
            case "yScale" -> u.yScale();
            case "yOffset" -> u.yOffset();
            default -> throw new HostError("udim2 has no member '%s'", key);
        };
    }

    static Object operate(Host.Op op, Object a, Object b) {
        return switch (op) {
            case ADD -> switch (a) {
                case Vector3 v when b instanceof Vector3 w -> v.add(w);
                case UDim2 u when b instanceof UDim2 w -> u.add(w);
                default -> throw mismatch("add", a, b);
            };
            case SUB -> switch (a) {
                case Vector3 v when b instanceof Vector3 w -> v.sub(w);
                case UDim2 u when b instanceof UDim2 w -> u.sub(w);
                default -> throw mismatch("subtract", a, b);
            };
            case MUL -> switch (a) {
                case Vector3 v when b instanceof Number n -> v.mul(n.doubleValue());
                case Number n when b instanceof Vector3 v -> v.mul(n.doubleValue());
                case Vector3 v when b instanceof Vector3 w -> v.mul(w);
                case CFrame c when b instanceof CFrame d -> c.mul(d);
                case CFrame c when b instanceof Vector3 v -> c.pointToWorld(v);
                case Quat q when b instanceof Quat r -> q.mul(r);
                default -> throw mismatch("multiply", a, b);
            };
            case DIV -> switch (a) {
                case Vector3 v when b instanceof Number n -> v.mul(1 / n.doubleValue());
                case Vector3 v when b instanceof Vector3 w -> new Vector3(v.x() / w.x(), v.y() / w.y(), v.z() / w.z());
                default -> throw mismatch("divide", a, b);
            };
            case UNM -> switch (a) {
                case Vector3 v -> v.neg();
                default -> throw new HostError("attempt to negate a %s", Host.typeOf(a));
            };
        };
    }

    private static HostError mismatch(String verb, Object a, Object b) {
        return new HostError("attempt to %s a %s and a %s", verb, Host.typeOf(a), Host.typeOf(b));
    }

    static String text(Object value) {
        return switch (value) {
            case Vector3 v -> "vec3(" + v.x() + ", " + v.y() + ", " + v.z() + ")";
            case CFrame c -> "cframe(" + c.position().x() + ", " + c.position().y() + ", " + c.position().z() + ")";
            case Quat q -> "quat(" + q.x() + ", " + q.y() + ", " + q.z() + ", " + q.w() + ")";
            case Color c -> "color(" + c.r() + ", " + c.g() + ", " + c.b() + ", " + c.a() + ")";
            case UDim2 u -> "udim2(" + u.xScale() + ", " + u.xOffset() + ", " + u.yScale() + ", " + u.yOffset() + ")";
            case Double d when d == Math.rint(d) && Math.abs(d) < 1e15 -> String.valueOf(d.longValue());
            case Map<?, ?> m -> "table";
            case List<?> l -> "table";
            default -> String.valueOf(value);
        };
    }

    public static boolean isValue(Object value) {
        return value instanceof Vector3 || value instanceof CFrame || value instanceof Quat
                || value instanceof Color || value instanceof UDim2;
    }

    public static double angle(Vector3 a, Vector3 b) {
        double lengths = a.length() * b.length();
        return lengths < 1e-12 ? 0 : Math.acos(Math.clamp(a.dot(b) / lengths, -1, 1));
    }

    public static Quat fromTo(Vector3 from, Vector3 to) {
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
}
