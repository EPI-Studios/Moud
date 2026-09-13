package com.meekdev.moud.core.scene;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Scene {

    public static final int FORMAT = 1;

    private Scene() {}

    public static String save(List<Instance> roots) {
        Map<Instance, Integer> ids = new IdentityHashMap<>();
        for (Instance root : roots) number(root, ids);
        Set<Instance> referenced = new HashSet<>();
        for (Instance root : roots) referenced(root, ids, referenced);

        List<Object> nodes = new ArrayList<>();
        for (Instance root : roots) nodes.add(node(root, ids, referenced));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("format", FORMAT);
        document.put("instances", nodes);
        return Json.write(document);
    }

    private static void number(Instance instance, Map<Instance, Integer> ids) {
        ids.put(instance, ids.size() + 1);
        for (Instance child : instance.children()) number(child, ids);
    }

    private static void referenced(Instance instance, Map<Instance, Integer> ids, Set<Instance> out) {
        for (PropertyDef property : instance.def().properties()) {
            if (property.type() == PropertyType.REF
                    && property.getObj(instance) instanceof Instance target && ids.containsKey(target)) {
                out.add(target);
            }
        }
        for (Instance child : instance.children()) referenced(child, ids, out);
    }

    private static Map<String, Object> node(Instance instance, Map<Instance, Integer> ids, Set<Instance> referenced) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("class", instance.def().name());
        node.put("name", instance.name());
        if (referenced.contains(instance)) node.put("id", ids.get(instance));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (PropertyDef property : instance.def().properties()) {
            Object value = read(instance, property);
            if (Objects.equals(value, property.defaultValue()) || isDefaultNumber(property, value)) continue;
            properties.put(property.name(), encode(property, value, ids));
        }
        if (!properties.isEmpty()) node.put("properties", properties);
        if (!instance.tags().isEmpty()) node.put("tags", new ArrayList<>(instance.tags()));
        List<Object> children = new ArrayList<>();
        for (Instance child : instance.children()) children.add(node(child, ids, referenced));
        if (!children.isEmpty()) node.put("children", children);
        return node;
    }

    private static Object read(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.isNumeric()) return property.getNum(instance);
        return property.getObj(instance);
    }

    private static boolean isDefaultNumber(PropertyDef property, Object value) {
        return property.isNumeric() && property.defaultValue() instanceof Number d
                && ((Number) value).doubleValue() == d.doubleValue();
    }

    private static Object encode(PropertyDef property, Object value, Map<Instance, Integer> ids) {
        return switch (property.type()) {
            case BOOL, STRING, ASSET -> value;
            case INT, NUM -> ((Number) value).doubleValue();
            case VEC3 -> vector((Vector3) value);
            case QUAT -> quat((Quat) value);
            case CFRAME -> {
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("position", vector(((CFrame) value).position()));
                frame.put("rotation", quat(((CFrame) value).rotation()));
                yield frame;
            }
            case COLOR -> {
                Color c = (Color) value;
                yield List.of((double) c.r(), (double) c.g(), (double) c.b(), (double) c.a());
            }
            case UDIM2 -> {
                UDim2 u = (UDim2) value;
                yield List.of(u.xScale(), u.xOffset(), u.yScale(), u.yOffset());
            }
            case ENUM -> Enums.name((Enum<?>) value);
            case REF -> value instanceof Instance target && ids.containsKey(target)
                    ? Map.of("ref", ids.get(target)) : null;
        };
    }

    private static List<Object> vector(Vector3 v) {
        return List.of(v.x(), v.y(), v.z());
    }

    private static List<Object> quat(Quat q) {
        return List.of(q.x(), q.y(), q.z(), q.w());
    }

    public static List<Instance> load(String text, Instance parent, ClassRegistry classes) {
        if (!(Json.parse(text) instanceof Map<?, ?> document)) throw new IllegalArgumentException("scene must be a json object");
        Object format = document.get("format");
        if (!(format instanceof Double f) || f.intValue() != FORMAT) {
            throw new IllegalArgumentException("unsupported scene format " + format + ", expected " + FORMAT);
        }
        if (!(document.get("instances") instanceof List<?> nodes)) {
            throw new IllegalArgumentException("scene has no \"instances\" list");
        }
        Loading loading = new Loading(classes);
        List<Instance> roots = new ArrayList<>();
        for (Object node : nodes) roots.add(loading.build(node, parent, parent.name()));
        loading.resolve();
        return roots;
    }

    private static final class Loading {

        private record Pending(Instance instance, PropertyDef property, int id, String where) {}

        private final ClassRegistry classes;
        private final Map<Integer, Instance> byId = new LinkedHashMap<>();
        private final List<Pending> pending = new ArrayList<>();
        private final Set<Instance> made = Collections.newSetFromMap(new IdentityHashMap<>());

        Loading(ClassRegistry classes) {
            this.classes = classes;
        }

        Instance build(Object raw, Instance parent, String path) {
            if (!(raw instanceof Map<?, ?> node)) throw new IllegalArgumentException(path + ": instance must be an object");
            String className = text(node, "class", path);
            String name = node.get("name") instanceof String n ? n : className;
            String where = path + "/" + name;
            ClassDef<?> def = classes.find(className);
            if (def == null) throw new IllegalArgumentException(where + ": unknown class " + className);

            Instance existing = parent.child(name);
            Instance instance = existing != null && existing.def() == def && !made.contains(existing)
                    ? existing : Instances.create(def, parent, name);
            made.add(instance);
            if (node.get("id") instanceof Double id) byId.put(id.intValue(), instance);

            if (node.get("properties") instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    PropertyDef property = def.property(key);
                    if (property == null) throw new IllegalArgumentException(where + ": unknown property " + className + "." + key);
                    write(instance, property, entry.getValue(), where);
                }
            }
            if (node.get("tags") instanceof List<?> tags) {
                for (Object tag : tags) Instances.addTag(instance, String.valueOf(tag));
            }
            if (node.get("children") instanceof List<?> children) {
                for (Object child : children) build(child, instance, where);
            }
            return instance;
        }

        void resolve() {
            for (Pending one : pending) {
                Instance target = byId.get(one.id());
                if (target == null) throw new IllegalArgumentException(one.where() + ": no instance with id " + one.id());
                Instances.setObj(one.instance(), one.property(), target);
            }
        }

        private void write(Instance instance, PropertyDef property, Object raw, String where) {
            String at = where + "." + property.name();
            try {
                switch (property.type()) {
                    case BOOL -> Instances.setBool(instance, property, (Boolean) raw);
                    case INT, NUM -> Instances.setNum(instance, property, ((Number) raw).doubleValue());
                    case STRING, ASSET -> Instances.setObj(instance, property, (String) raw);
                    case VEC3 -> Instances.setObj(instance, property, vec3(raw));
                    case QUAT -> Instances.setObj(instance, property, quat(raw));
                    case CFRAME -> {
                        Map<?, ?> frame = (Map<?, ?>) raw;
                        Instances.setObj(instance, property, new CFrame(vec3(frame.get("position")), quat(frame.get("rotation"))));
                    }
                    case COLOR -> {
                        double[] c = numbers(raw, 4);
                        Instances.setObj(instance, property, new Color((float) c[0], (float) c[1], (float) c[2], (float) c[3]));
                    }
                    case UDIM2 -> {
                        double[] u = numbers(raw, 4);
                        Instances.setObj(instance, property, new UDim2(u[0], u[1], u[2], u[3]));
                    }
                    case ENUM -> Instances.setObj(instance, property,
                            Enums.parse(((Enum<?>) property.defaultValue()).getDeclaringClass(), (String) raw));
                    case REF -> {
                        if (raw == null) {
                            Instances.setObj(instance, property, null);
                        } else {
                            pending.add(new Pending(instance, property, ((Number) ((Map<?, ?>) raw).get("ref")).intValue(), at));
                        }
                    }
                }
            } catch (ClassCastException | NullPointerException ignored) {
                throw new IllegalArgumentException(at + ": expected " + property.type().name().toLowerCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(at + ": " + e.getMessage());
            }
        }

        private static String text(Map<?, ?> node, String key, String where) {
            if (!(node.get(key) instanceof String s)) throw new IllegalArgumentException(where + ": missing \"" + key + "\"");
            return s;
        }

        private static Vector3 vec3(Object raw) {
            double[] v = numbers(raw, 3);
            return new Vector3(v[0], v[1], v[2]);
        }

        private static Quat quat(Object raw) {
            double[] q = numbers(raw, 4);
            return new Quat(q[0], q[1], q[2], q[3]).normalize();
        }

        private static double[] numbers(Object raw, int count) {
            List<?> list = (List<?>) raw;
            if (list.size() != count) throw new ClassCastException();
            double[] out = new double[count];
            for (int i = 0; i < count; i++) out[i] = ((Number) list.get(i)).doubleValue();
            return out;
        }
    }
}
