package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.query.SpatialIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class InstanceTree {

    public record TagChange(int id, String tag, boolean added) {}

    @FunctionalInterface
    public interface DirtyVisitor {
        void visit(Instance instance, long mask);
    }

    private static final class IdList {
        private int[] ids = new int[16];
        private int count;

        void add(int id) {
            if (count == ids.length) ids = Arrays.copyOf(ids, ids.length * 2);
            ids[count++] = id;
        }

        void drain(IntConsumer visitor) {
            for (int n = 0; n < count; n++) visitor.accept(ids[n]);
            count = 0;
        }
    }

    public final Set<Instance> spatialTouched = Collections.newSetFromMap(new IdentityHashMap<>());

    final boolean mirror;
    int nextId = 1;
    int nextLocalId = -1;
    long structureEpoch = 1;
    long mutations;

    private Instance root;
    private Instance[] byId = new Instance[64];
    private Instance[] byLocalId = new Instance[16];
    private final Map<ClassDef<?>, List<Instance>> byClass = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final List<Instance>[] byStage = new List[Stage.ORDER.length];
    private final Map<String, List<Instance>> byTag = new HashMap<>();
    private final Map<String, Signal<Instance>> tagAdded = new HashMap<>();
    private final Map<String, Signal<Instance>> tagRemoved = new HashMap<>();
    private final List<TagChange> tagChanges = new ArrayList<>();
    private final IdList dirty = new IdList();
    private final IdList removed = new IdList();
    private final IdList moved = new IdList();
    private final IdList renamed = new IdList();
    private Instance[] scratch = new Instance[32];
    private SpatialIndex spatial;

    public InstanceTree() {
        this(false);
    }

    public InstanceTree(boolean mirror) {
        this.mirror = mirror;
    }

    public boolean mirror() {
        return mirror;
    }

    public Instance root() {
        return root;
    }

    void setRoot(Instance instance) {
        root = instance;
    }

    public long mutations() {
        return mutations + structureEpoch;
    }

    public long structureEpoch() {
        return structureEpoch;
    }

    public SpatialIndex spatial() {
        if (spatial == null) spatial = new SpatialIndex(this);
        return spatial;
    }

    public int highestId() {
        return nextId - 1;
    }

    public Instance byId(int id) {
        Instance[] slots = id < 0 ? byLocalId : byId;
        int slot = Math.abs(id);
        if (slot >= slots.length) return null;
        Instance i = slots[slot];
        return i != null && i.id == id ? i : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Instance> List<T> ofClass(ClassDef<T> def) {
        List<Instance> list = byClass.get(def);
        return list == null ? List.of() : (List<T>) (List<?>) list;
    }

    public List<Instance> inStage(Stage stage) {
        List<Instance> list = byStage[stage.ordinal()];
        return list == null ? List.of() : list;
    }

    public List<Instance> tagged(String tag) {
        List<Instance> list = byTag.get(tag);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public Signal<Instance> tagAdded(String tag) {
        return tagAdded.computeIfAbsent(tag, t -> new Signal<>());
    }

    public Signal<Instance> tagRemoved(String tag) {
        return tagRemoved.computeIfAbsent(tag, t -> new Signal<>());
    }

    public int dirtyCount() {
        return dirty.count;
    }

    public int removedCount() {
        return removed.count;
    }

    public void drainDirty(DirtyVisitor visitor) {
        dirty.drain(id -> {
            Instance i = byId(id);
            if (i == null || i.dirty == 0) return;
            visitor.visit(i, i.dirty);
            i.dirty = 0;
        });
    }

    public void drainRemoved(IntConsumer visitor) {
        removed.drain(visitor);
    }

    public void drainMoved(IntConsumer visitor) {
        moved.drain(visitor);
    }

    public void drainRenamed(IntConsumer visitor) {
        renamed.drain(visitor);
    }

    public void drainTags(Consumer<TagChange> visitor) {
        for (TagChange change : tagChanges) visitor.accept(change);
        tagChanges.clear();
    }

    void markDirty(Instance i) {
        dirty.add(i.id);
    }

    void markMoved(Instance i) {
        moved.add(i.id);
    }

    void markRenamed(Instance i) {
        renamed.add(i.id);
    }

    void index(Instance i) {
        int slot = Math.abs(i.id);
        if (i.id < 0) {
            if (slot >= byLocalId.length) byLocalId = Arrays.copyOf(byLocalId, Math.max(slot + 1, byLocalId.length * 2));
            byLocalId[slot] = i;
        } else {
            if (slot >= byId.length) byId = Arrays.copyOf(byId, Math.max(slot + 1, byId.length * 2));
            byId[slot] = i;
        }
        for (ClassDef<?> c = i.def(); c != null; c = c.parent()) {
            byClass.computeIfAbsent(c, k -> new ArrayList<>()).add(i);
        }
        for (Stage stage : Stage.ORDER) {
            if (!i.def().takesPart(stage)) continue;
            if (byStage[stage.ordinal()] == null) byStage[stage.ordinal()] = new ArrayList<>();
            byStage[stage.ordinal()].add(i);
        }
        structureEpoch++;
    }

    void unindex(Instance i) {
        Instance[] slots = i.id < 0 ? byLocalId : byId;
        int slot = Math.abs(i.id);
        if (slot < slots.length && slots[slot] == i) slots[slot] = null;
        for (ClassDef<?> c = i.def(); c != null; c = c.parent()) {
            List<Instance> list = byClass.get(c);
            if (list != null) list.remove(i);
        }
        for (Stage stage : Stage.ORDER) {
            List<Instance> staged = byStage[stage.ordinal()];
            if (staged != null && i.def().takesPart(stage)) staged.remove(i);
        }
        removed.add(i.id);
        structureEpoch++;
        if (i.tags == null) return;
        for (String tag : i.tags) {
            List<Instance> list = byTag.get(tag);
            if (list != null) list.remove(i);
            Signal<Instance> signal = tagRemoved.get(tag);
            if (signal != null) signal.fire(i);
        }
    }

    void tag(Instance i, String tag, boolean added) {
        if (added) {
            byTag.computeIfAbsent(tag, t -> new ArrayList<>()).add(i);
        } else {
            List<Instance> list = byTag.get(tag);
            if (list != null) list.remove(i);
        }
        if (i.id > 0) tagChanges.add(new TagChange(i.id, tag, added));
        Signal<Instance> signal = (added ? tagAdded : tagRemoved).get(tag);
        if (signal != null) signal.fire(i);
    }

    Instance[] snapshot(List<Instance> of) {
        if (scratch.length < of.size()) scratch = new Instance[Math.max(of.size(), scratch.length * 2)];
        for (int n = 0; n < of.size(); n++) scratch[n] = of.get(n);
        return scratch;
    }
}
