package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
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
        }
        seen = highest;

        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance != null && instance.parent() != null) {
                out.accept(new Change.Moved(id, instance.parent().id()));
            }
        });

        tree.drainTags(tag -> {
            if (tag.id() <= before) out.accept(new Change.Tagged(tag.id(), tag.tag(), tag.added()));
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
