package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ClassDef<T extends Instance> {

    public static final int MAX_PROPERTIES = 64;

    private final String name;
    private final ClassDef<?> parent;
    private final Supplier<T> factory;
    private final PropertyDef[] byIndex;
    private final Map<String, PropertyDef> byName;

    private ClassDef(String name, ClassDef<?> parent, Supplier<T> factory, List<PropertyDef> props) {
        this.name = name;
        this.parent = parent;
        this.factory = factory;
        this.byIndex = props.toArray(new PropertyDef[0]);
        this.byName = new HashMap<>(props.size() * 2);
        for (PropertyDef p : props) byName.put(p.name(), p);
    }

    public static <T extends Instance> ClassDef<T> of(String name, ClassDef<?> parent, Class<T> type, Supplier<T> factory) {
        List<PropertyDef> props = new ArrayList<>();
        // inherited properties keep their indices so a subclass mask means the same bits
        if (parent != null) props.addAll(List.of(parent.byIndex));

        Instance prototype = factory.get();
        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot reach the fields of " + name, e);
        }

        List<Field> fields = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods) || Modifier.isFinal(mods)) continue;
            fields.add(f);
        }
        // sorted rather than declaration order, so a property index never depends on the jvm
        fields.sort(Comparator.comparing(Field::getName));

        for (Field f : fields) {
            if (props.size() >= MAX_PROPERTIES) {
                throw new IllegalStateException(name + " has more than " + MAX_PROPERTIES
                        + " properties, the class is doing too much");
            }
            props.add(define(name, type, f, prototype, lookup, props.size()));
        }
        return new ClassDef<>(name, parent, factory, props);
    }

    private static PropertyDef define(String owner, Class<?> type, Field field, Instance prototype,
                                      MethodHandles.Lookup lookup, int index) {
        PropertyType kind = kindOf(field.getType());
        if (kind == null) {
            throw new IllegalStateException(owner + "." + field.getName() + " is a " + field.getType().getSimpleName()
                    + ", which is not a property type. make it private, or add the type to PropertyType");
        }
        VarHandle handle;
        try {
            handle = lookup.findVarHandle(type, field.getName(), field.getType());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach " + owner + "." + field.getName(), e);
        }

        Prop opts = field.getAnnotation(Prop.class);
        boolean replicated = opts == null || opts.replicated();
        double min = opts == null ? Double.NEGATIVE_INFINITY : opts.min();
        double max = opts == null ? Double.POSITIVE_INFINITY : opts.max();

        return PropertyDef.of(field.getName(), kind, index, replicated, handle.get(prototype), min, max, handle);
    }

    private static PropertyType kindOf(Class<?> t) {
        if (t == double.class || t == float.class) return PropertyType.NUM;
        if (t == int.class) return PropertyType.INT;
        if (t == boolean.class) return PropertyType.BOOL;
        if (t == String.class) return PropertyType.STRING;
        if (t == Vec3.class) return PropertyType.VEC3;
        if (t == Quat.class) return PropertyType.QUAT;
        if (t == CFrame.class) return PropertyType.CFRAME;
        if (t == Color.class) return PropertyType.COLOR;
        if (t.isEnum()) return PropertyType.ENUM;
        return null;
    }

    public String name() { return name; }
    public ClassDef<?> parent() { return parent; }
    public PropertyDef[] properties() { return byIndex; }

    public PropertyDef property(String name) {
        return byName.get(name);
    }

    public PropertyDef property(int index) {
        return index >= 0 && index < byIndex.length ? byIndex[index] : null;
    }

    public T create() {
        T i = factory.get();
        i.attachClass(this);
        return i;
    }

    public boolean isA(ClassDef<?> other) {
        for (ClassDef<?> c = this; c != null; c = c.parent) {
            if (c == other) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
