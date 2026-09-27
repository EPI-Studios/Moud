package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Recorder {

    private InstanceTree tree;
    private int seen;

    public void follow(InstanceTree next, Consumer<Change> out) {
        if (next == tree) return;
        tree = next;
        seen = 0;
        out.accept(new Change.Reset());
    }

    public void drain(Consumer<Change> out) {
        if (tree == null) return;

        tree.drainRemoved(id -> out.accept(new Change.Destroyed(id)));

        int before = seen;
        int highest = tree.highestId();
        for (int id = seen + 1; id <= highest; id++) {
            Instance instance = tree.byId(id);
            if (instance == null || instance.parent() == null) continue;
            out.accept(new Change.Created(id, instance.def().name(), instance.parent().id(), instance.name()));
            long skip = instance.def().unreplicated() | instance.externalProperties();
            for (PropertyDef property : instance.def().properties()) {
                if ((skip & (1L << property.index())) != 0) continue;
                out.accept(new Change.Wrote(id, property.index(), read(instance, property)));
            }
            for (String tag : instance.tags()) out.accept(new Change.Tagged(id, tag, true));
            for (Map.Entry<String, Object> attribute : instance.attributes().entrySet()) {
                out.accept(new Change.Attributed(id, attribute.getKey(), attribute.getValue()));
            }
        }
        seen = highest;

        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance != null && instance.parent() != null) {
                out.accept(new Change.Moved(id, instance.parent().id()));
            }
        });

        List<Instance> ordered = new ArrayList<>();
        tree.drainOrdered(id -> {
            Instance instance = tree.byId(id);
            if (instance != null && instance.parent() != null && !ordered.contains(instance)) ordered.add(instance);
        });
        ordered.sort(Comparator.comparingInt(instance -> instance.parent().children().indexOf(instance)));
        for (Instance instance : ordered) out.accept(new Change.Ordered(instance.id(), before(instance)));

        tree.drainRenamed(id -> {
            Instance instance = tree.byId(id);
            if (id <= before && instance != null && instance.parent() != null) out.accept(new Change.Renamed(id, instance.name()));
        });

        tree.drainTags(tag -> {
            if (tag.id() <= before) out.accept(new Change.Tagged(tag.id(), tag.tag(), tag.added()));
        });

        tree.drainAttributes(change -> {
            Instance instance = tree.byId(change.id());
            if (change.id() <= before && instance != null) {
                out.accept(new Change.Attributed(change.id(), change.name(), instance.attribute(change.name())));
            }
        });

        tree.drainDirty((instance, mask) -> {
            if (instance.id() > seen) return;
            mask &= ~(instance.def().unreplicated() | instance.externalProperties());
            for (PropertyDef property : instance.def().properties()) {
                if ((mask & (1L << property.index())) == 0) continue;
                out.accept(new Change.Wrote(instance.id(), property.index(), read(instance, property)));
            }
        });
    }

    private static int before(Instance instance) {
        List<Instance> siblings = instance.parent().children();
        for (int at = siblings.indexOf(instance) - 1; at >= 0; at--) {
            if (siblings.get(at).id() > 0) return siblings.get(at).id();
        }
        return 0;
    }

    static Object read(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.isNumeric()) return property.getNum(instance);
        if (property.type() == PropertyType.REF) {
            Object target = property.getObj(instance);
            return target instanceof Instance pointed ? pointed.id() : null;
        }
        return property.getObj(instance);
    }
}
