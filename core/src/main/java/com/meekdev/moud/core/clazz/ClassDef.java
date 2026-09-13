package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
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

    private final Map<String, EventDef> events;

    private Map<String, CallbackDef> callbacks = Map.of();

    private final int stages;

    private final long unreplicated;
    private final long driven;

    private ClassDef(String name, ClassDef<?> parent, Supplier<T> factory, List<PropertyDef> props,
                     Map<String, EventDef> events, int stages) {
        this.name = name;
        this.parent = parent;
        this.factory = factory;
        this.byIndex = props.toArray(new PropertyDef[0]);
        this.byName = new HashMap<>(props.size() * 2);
        for (PropertyDef p : props) byName.put(p.name(), p);
        this.events = Map.copyOf(events);
        this.stages = stages;
        long off = 0;
        long fromElsewhere = 0;
        for (PropertyDef p : props) {
            if (!p.replicated()) off |= 1L << p.index();
            if (p.driven()) fromElsewhere |= 1L << p.index();
        }
        this.unreplicated = off;
        this.driven = fromElsewhere;
    }

    public static <T extends Instance> ClassDef<T> of(String name, ClassDef<?> parent, Class<T> type, Supplier<T> factory) {
        List<PropertyDef> props = new ArrayList<>();
        if (parent != null) props.addAll(List.of(parent.byIndex));

        Instance prototype = factory.get();
        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot reach the fields of " + name, e);
        }

        Map<String, EventDef> events = new HashMap<>();
        if (parent != null) events.putAll(parent.events);
        Map<String, CallbackDef> callbacks = new HashMap<>();
        if (parent != null) callbacks.putAll(parent.callbacks);

        List<Field> fields = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) continue;
            if (Modifier.isFinal(mods)) {
                if (Signal.class.isAssignableFrom(f.getType())) {
                    events.put(f.getName(), new EventDef(f.getName(), f));
                }
                if (Callback.class.isAssignableFrom(f.getType())) {
                    callbacks.put(f.getName(), new CallbackDef(f.getName(), f));
                }
                continue;
            }
            fields.add(f);
        }
        fields.sort(Comparator.comparing(Field::getName));

        for (Field f : fields) {
            if (props.size() >= MAX_PROPERTIES) {
                throw new IllegalStateException(name + " has more than " + MAX_PROPERTIES
                        + " properties, the class is doing too much");
            }
            props.add(define(name, type, f, prototype, lookup, props.size()));
        }
        ClassDef<T> def = new ClassDef<>(name, parent, factory, props, events, stagesOf(type));
        def.callbacks = Map.copyOf(callbacks);
        return def;
    }

    private static int stagesOf(Class<?> type) {
        int bits = 0;
        for (Stage stage : Stage.ORDER) {
            for (Class<?> c = type; c != null && c != Instance.class; c = c.getSuperclass()) {
                if (declares(c, stage)) {
                    bits |= stage.bit;
                    break;
                }
            }
        }
        return bits;
    }

    private static boolean declares(Class<?> type, Stage stage) {
        try {
            type.getDeclaredMethod(stage.method(),
                    stage.timed() ? new Class<?>[] {double.class} : new Class<?>[0]);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
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
        boolean driven = opts != null && opts.driven();
        boolean asset = opts != null && opts.asset();
        if (asset && kind != PropertyType.STRING && kind != PropertyType.ASSET) {
            throw new IllegalStateException(owner + "." + field.getName() + " is a " + kind
                    + ", and only text names a file");
        }
        if (driven && !replicated) {
            throw new IllegalStateException(owner + "." + field.getName()
                    + " is both driven and not replicated, and driven means replicated sometimes");
        }
        double min = opts == null ? Double.NEGATIVE_INFINITY : opts.min();
        double max = opts == null ? Double.POSITIVE_INFINITY : opts.max();

        return PropertyDef.of(field.getName(), kind, index, replicated, driven, asset,
                handle.get(prototype), min, max, handle);
    }

    private static PropertyType kindOf(Class<?> t) {
        if (t == double.class || t == float.class) return PropertyType.NUM;
        if (t == int.class) return PropertyType.INT;
        if (t == boolean.class) return PropertyType.BOOL;
        if (t == String.class) return PropertyType.STRING;
        if (t == Vec3.class) return PropertyType.VEC3;
        if (t == UDim2.class) return PropertyType.UDIM2;
        if (t == Quat.class) return PropertyType.QUAT;
        if (t == CFrame.class) return PropertyType.CFRAME;
        if (t == Color.class) return PropertyType.COLOR;
        if (t.isEnum()) return PropertyType.ENUM;
        if (Instance.class.isAssignableFrom(t)) return PropertyType.REF;
        return null;
    }

    public String name() { return name; }
    public ClassDef<?> parent() { return parent; }
    public PropertyDef[] properties() { return byIndex; }

    public long unreplicated() { return unreplicated; }

    public long driven() { return driven; }

    public EventDef event(String name) { return events.get(name); }

    public CallbackDef callback(String name) { return callbacks.get(name); }

    public java.util.Collection<CallbackDef> callbacks() { return callbacks.values(); }

    public boolean takesPart(Stage stage) { return (stages & stage.bit) != 0; }

    public int stages() { return stages; }

    public java.util.Collection<EventDef> events() { return events.values(); }

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
