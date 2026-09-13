package com.meekdev.moud.core.instance;

import java.util.IdentityHashMap;
import java.util.Set;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.event.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import com.meekdev.moud.core.query.SpatialIndex;

public final class InstanceTree {

    private final Map<ClassDef<?>, List<Instance>> byClass = new HashMap<>();

    @SuppressWarnings("unchecked")
    private final List<Instance>[] byStage = new List[Stage.ORDER.length];

    private Instance[] scratch = new Instance[32];

    Instance[] byId = new Instance[64];
    int nextId = 1;
    int nextLocalId = -1;

    final boolean mirror;

    int[] dirtyList = new int[64];
    int dirtyCount;

    int[] removedList = new int[16];
    int removedCount;

    long structureEpoch = 1;

    private Instance root;

    public InstanceTree() {
        this(false);
    }

    public InstanceTree(boolean mirror) {
        this.mirror = mirror;
    }

    public boolean mirror() { return mirror; }

    public Instance root() { return root; }

    public final Set<Instance> spatialTouched = Collections.newSetFromMap(new IdentityHashMap<>());
    private SpatialIndex spatial;

    long mutations;

    public long mutations() {
        return mutations + structureEpoch;
    }

    public SpatialIndex spatial() {
        if (spatial == null) spatial = new SpatialIndex(this);
        return spatial;
    }

    public long structureEpoch() { return structureEpoch; }

    public Instance byId(int id) {
        int slot = id < 0 ? -id : id;
        if (slot >= byId.length) return null;
        Instance i = byId[slot];
        return i != null && i.id == id ? i : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Instance> List<T> ofClass(ClassDef<T> def) {
        List<Instance> list = byClass.get(def);
        return list == null ? List.of() : (List<T>) (List<?>) list;
    }

    public int dirtyCount() {
        return dirtyCount;
    }

    void setRoot(Instance instance) {
        this.root = instance;
    }

    void index(Instance i) {
        int slot = i.id < 0 ? -i.id : i.id;
        if (slot >= byId.length) {
            Instance[] grown = new Instance[Math.max(slot + 1, byId.length * 2)];
            System.arraycopy(byId, 0, grown, 0, byId.length);
            byId = grown;
        }
        byId[slot] = i;
        for (ClassDef<?> c = i.def(); c != null; c = c.parent()) {
            byClass.computeIfAbsent(c, k -> new ArrayList<>()).add(i);
        }
        for (Stage stage : Stage.ORDER) {
            if (!i.def().takesPart(stage)) continue;
            List<Instance> list = byStage[stage.ordinal()];
            if (list == null) byStage[stage.ordinal()] = list = new ArrayList<>();
            list.add(i);
        }
        structureEpoch++;
    }

    public List<Instance> inStage(Stage stage) {
        List<Instance> list = byStage[stage.ordinal()];
        return list == null ? List.of() : list;
    }

    Instance[] snapshot(List<Instance> of) {
        if (scratch.length < of.size()) scratch = new Instance[Math.max(of.size(), scratch.length * 2)];
        for (int n = 0; n < of.size(); n++) scratch[n] = of.get(n);
        return scratch;
    }

    public int highestId() {
        return nextId - 1;
    }

    public int removedCount() {
        return removedCount;
    }

    public void drainRemoved(IntConsumer visitor) {
        for (int n = 0; n < removedCount; n++) visitor.accept(removedList[n]);
        removedCount = 0;
    }

    public void drainMoved(IntConsumer visitor) {
        for (int n = 0; n < movedCount; n++) visitor.accept(movedList[n]);
        movedCount = 0;
    }

    void markMoved(Instance i) {
        if (movedCount == movedList.length) {
            int[] grown = new int[movedList.length * 2];
            System.arraycopy(movedList, 0, grown, 0, movedList.length);
            movedList = grown;
        }
        movedList[movedCount++] = i.id;
    }

    void unindex(Instance i) {
        int slot = i.id < 0 ? -i.id : i.id;
        if (slot < byId.length && byId[slot] == i) byId[slot] = null;
        for (ClassDef<?> c = i.def(); c != null; c = c.parent()) {
            List<Instance> list = byClass.get(c);
            if (list != null) list.remove(i);
        }
        for (Stage stage : Stage.ORDER) {
            if (!i.def().takesPart(stage)) continue;
            List<Instance> staged = byStage[stage.ordinal()];
            if (staged != null) staged.remove(i);
        }
        if (removedCount == removedList.length) {
            int[] grown = new int[removedList.length * 2];
            System.arraycopy(removedList, 0, grown, 0, removedList.length);
            removedList = grown;
        }
        removedList[removedCount++] = i.id;
        structureEpoch++;
        if (i.tags != null) {
            for (String tag : i.tags) {
                List<Instance> list = byTag.get(tag);
                if (list != null) list.remove(i);
                Signal<Instance> signal = tagRemoved.get(tag);
                if (signal != null) signal.fire(i);
            }
        }
    }

    public record TagChange(int id, String tag, boolean added) {}

    private final Map<String, List<Instance>> byTag = new HashMap<>();
    private final Map<String, Signal<Instance>> tagAdded = new HashMap<>();
    private final Map<String, Signal<Instance>> tagRemoved = new HashMap<>();
    private final List<TagChange> tagChanges = new ArrayList<>();

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

    public void drainTags(Consumer<TagChange> visitor) {
        for (TagChange change : tagChanges) visitor.accept(change);
        tagChanges.clear();
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

    private int[] movedList = new int[8];
    private int movedCount;

    void markDirty(Instance i) {
        if (dirtyCount == dirtyList.length) {
            int[] grown = new int[dirtyList.length * 2];
            System.arraycopy(dirtyList, 0, grown, 0, dirtyList.length);
            dirtyList = grown;
        }
        dirtyList[dirtyCount++] = i.id;
    }

    public void drainDirty(DirtyVisitor visitor) {
        for (int n = 0; n < dirtyCount; n++) {
            Instance i = byId(dirtyList[n]);
            if (i == null || i.dirty == 0) continue;
            visitor.visit(i, i.dirty);
            i.dirty = 0;
        }
        dirtyCount = 0;
    }

    @FunctionalInterface
    public interface DirtyVisitor {
        void visit(Instance instance, long mask);
    }
}
