package com.meekdev.moud.core.tween;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import java.util.List;
import java.util.function.Predicate;

public final class PropertyTrack extends Instance {

    public Instance target;

    public String path = "";

    public String property = "";

    public String keys = "";

    public Interpolation interpolation = Interpolation.LINEAR;

    public Easing easing = Easing.LINEAR;

    public Easing.Direction direction = Easing.Direction.IN_OUT;

    public boolean loopWrap = true;

    private String parsedFrom;
    private PropertyType parsedAs;
    private List<Keys.Key> parsed = List.of();
    private int reached = -1;

    public Instance resolve() {
        if (target != null) return target.isAlive() ? target : null;
        return parent() == null ? null : find(parent().parent(), path);
    }

    public static Instance find(Instance base, String path) {
        if (path.isBlank()) return null;
        Instance at = base;
        for (String step : path.split("/")) {
            if (at == null) return null;
            if (step.isEmpty() || step.equals(".")) continue;
            at = step.equals("..") ? at.parent() : at.child(step);
        }
        return at;
    }

    public PropertyDef propertyOf(Instance instance) {
        PropertyDef def = instance.def().property(property);
        return def == null || def.readOnly() || !Keys.animatable(def.type()) ? null : def;
    }

    public List<Keys.Key> keys(PropertyType type) {
        if (!keys.equals(parsedFrom) || type != parsedAs) {
            parsed = Keys.parse(keys, type);
            parsedFrom = keys;
            parsedAs = type;
        }
        return parsed;
    }

    public double end() {
        double end = 0;
        for (String entry : keys.split(",")) {
            String[] words = entry.trim().split("\\s+");
            try {
                end = Math.max(end, Double.parseDouble(words[0]));
            } catch (NumberFormatException ignored) {
            }
        }
        return end;
    }

    void rewind() {
        reached = -1;
    }

    void apply(double time, double length, boolean looped, Predicate<Instance> writes) {
        Instance instance = resolve();
        if (instance == null || !writes.test(instance)) return;
        PropertyDef def = propertyOf(instance);
        if (def == null) return;
        List<Keys.Key> list = keys(def.type());
        if (list.isEmpty()) return;
        if (interpolation == Interpolation.CONSTANT || def.type() == PropertyType.BOOL) {
            int at = Keys.index(list, time);
            if (at == reached) return;
            reached = at;
            if (at >= 0) write(instance, def, list.get(at).value());
            return;
        }
        Keys.Defaults defaults = new Keys.Defaults(interpolation, easing, direction);
        write(instance, def, Keys.sample(list, time, length, looped && loopWrap, defaults, Keys.rotation(def.type())));
    }

    private static void write(Instance instance, PropertyDef def, double[] value) {
        Object decoded = Keys.value(def.type(), value);
        switch (def.type()) {
            case BOOL -> Instances.setBool(instance, def, (Boolean) decoded);
            case INT, NUM -> Instances.setNum(instance, def, (Double) decoded);
            default -> Instances.setObj(instance, def, decoded);
        }
    }
}
