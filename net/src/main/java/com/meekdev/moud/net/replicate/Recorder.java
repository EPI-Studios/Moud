package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import java.util.function.Consumer;

// reads the authoritative tree and says what changed. ids are monotonic, so anything past the last
// one seen is new and no per instance bookkeeping is needed to notice a creation
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

        int highest = tree.highestId();
        for (int id = seen + 1; id <= highest; id++) {
            Instance instance = tree.byId(id);
            if (instance == null || instance.parent() == null) continue;
            out.accept(new Change.Created(id, instance.def().name(), instance.parent().id(), instance.name()));
            for (PropertyDef property : instance.def().properties()) {
                out.accept(new Change.Wrote(id, property.index(), read(instance, property)));
            }
        }
        seen = highest;

        tree.drainDirty((instance, mask) -> {
            if (instance.id() > seen) return;
            for (PropertyDef property : instance.def().properties()) {
                if ((mask & (1L << property.index())) == 0) continue;
                out.accept(new Change.Wrote(instance.id(), property.index(), read(instance, property)));
            }
        });
    }

    private static Object read(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.isNumeric()) return property.getNum(instance);
        return property.getObj(instance);
    }
}
