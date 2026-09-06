package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.ClassDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public final class InstanceTree {

    private final Map<ClassDef<?>, List<Instance>> byClass = new HashMap<>();

    Instance[] byId = new Instance[64];
    int nextId = 1;
    int nextLocalId = -1;

    int[] dirtyList = new int[64];
    int dirtyCount;

    // ids that left the tree since the last drain, which a mirror needs and the renderer does not
    int[] removedList = new int[16];
    int removedCount;

    long structureEpoch = 1;

    private Instance root;

    public Instance root() { return root; }

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
        byClass.computeIfAbsent(i.def(), k -> new ArrayList<>()).add(i);
        structureEpoch++;
    }

    // the highest replicated id handed out so far, which is what lets a mirror spot new instances
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

    void unindex(Instance i) {
        int slot = i.id < 0 ? -i.id : i.id;
        if (slot < byId.length && byId[slot] == i) byId[slot] = null;
        List<Instance> list = byClass.get(i.def());
        if (list != null) list.remove(i);
        if (removedCount == removedList.length) {
            int[] grown = new int[removedList.length * 2];
            System.arraycopy(removedList, 0, grown, 0, removedList.length);
            removedList = grown;
        }
        removedList[removedCount++] = i.id;
        structureEpoch++;
    }

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
