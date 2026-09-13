package com.meekdev.moud.net.replicate;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
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

        int before = seen;
        int highest = tree.highestId();
        for (int id = seen + 1; id <= highest; id++) {
            Instance instance = tree.byId(id);
            if (instance == null || instance.parent() == null) continue;
            out.accept(new Change.Created(id, instance.def().name(), instance.parent().id(), instance.name()));
            long skip = instance.def().unreplicated() | instance.fromElsewhere();
            for (PropertyDef property : instance.def().properties()) {
                if ((skip & (1L << property.index())) != 0) continue;
                out.accept(new Change.Wrote(id, property.index(), read(instance, property)));
            }
            for (String tag : instance.tags()) out.accept(new Change.Tagged(id, tag, true));
        }
        seen = highest;

        // after the creations, so an instance that appeared and moved in the same tick is there to
        // be moved. the parent is read now rather than when it moved, which is the one that stuck
        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance != null && instance.parent() != null) {
                out.accept(new Change.Moved(id, instance.parent().id()));
            }
        });

        // an instance made this tick already went over with the tags it has now
        tree.drainTags(tag -> {
            if (tag.id() <= before) out.accept(new Change.Tagged(tag.id(), tag.tag(), tag.added()));
        });

        tree.drainDirty((instance, mask) -> {
            if (instance.id() > seen) return;
            // what stays on this side. the class's own never go over at all; the rest are what
            // somebody else is already telling the other side, which for a body a player is wearing
            // is most of what changed: the frame, where it is looking and how far it has walked,
            // twenty times a second, per player
            mask &= ~(instance.def().unreplicated() | instance.fromElsewhere());
            for (PropertyDef property : instance.def().properties()) {
                if ((mask & (1L << property.index())) == 0) continue;
                out.accept(new Change.Wrote(instance.id(), property.index(), read(instance, property)));
            }
        });
    }

    // shared with the audience, which reads exactly the same values for a baseline
    static Object read(Instance instance, PropertyDef property) {
        if (property.type().isBool()) return property.getBool(instance);
        if (property.isNumeric()) return property.getNum(instance);
        // a reference crosses as the id it points at, never as the instance
        //
        // the instance belongs to the authority's tree. handing it over put one tree's object
        // into the other's, so the mirror pointed at something the server thread was writing --
        // and anything that followed the reference read across the two
        if (property.type() == PropertyType.REF) {
            Object target = property.getObj(instance);
            return target instanceof Instance pointed ? pointed.id() : null;
        }
        return property.getObj(instance);
    }
}
