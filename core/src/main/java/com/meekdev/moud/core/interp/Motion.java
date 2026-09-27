package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.part.Part;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class Motion {

    private final Map<Instance, Track> tracks = new IdentityHashMap<>();

    private final Set<Instance> stepping = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<Instance> entered = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<Instance> moving = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<Instance, CFrame> composed = new IdentityHashMap<>();

    private final Map<Instance, CFrame> live = new IdentityHashMap<>();
    private double composedAt = Double.NaN;

    private final Set<String> looks;
    private final Map<ClassDef<?>, long[]> masks = new IdentityHashMap<>();

    private long structure;
    private boolean stillChanged = true;
    private boolean carriedStale = true;

    public Motion() {
        this(Set.of());
    }

    public Motion(Set<String> looks) {
        this.looks = looks;
    }

    public Collection<Instance> moving() {
        return moving;
    }

    public boolean isMoving(Instance instance) {
        return moving.contains(instance);
    }

    public boolean consumeStaticChanged() {
        boolean was = stillChanged;
        stillChanged = false;
        return was;
    }

    public void stillChanged() {
        stillChanged = true;
    }

    public void drain(InstanceTree tree) {
        if (tree.structureEpoch() != structure) {
            structure = tree.structureEpoch();
            if (tree.root() != null) adopt(tree.root());
            prune();
            carriedStale = true;
        }

        for (Instance instance : stepping) {
            Track track = tracks.get(instance);
            if (track != null) track.beginLeg();
        }

        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance == null) return;
            carriedStale = true;
            if (instance instanceof Spatial) {
                tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.local(instance)));
                stepping.remove(instance);
            }
            if (holdsSpatial(instance)) stillChanged = true;
        });

        tree.drainDirty((instance, mask) -> {
            if (!(instance instanceof Spatial)) return;
            long[] mine = masks.computeIfAbsent(instance.def(), this::masks);
            if ((mask & mine[0]) != 0) write(instance);
            if ((mask & mine[1]) != 0 && instance instanceof Part && !moving.contains(instance)) stillChanged = true;
        });

        for (Iterator<Instance> it = stepping.iterator(); it.hasNext(); ) {
            Instance instance = it.next();
            Track track = tracks.get(instance);
            if (track == null || track.isStill()) {
                it.remove();
                if (!entered.remove(instance)) {
                    stillChanged = true;
                    carriedStale = true;
                }
            }
        }
        if (!entered.isEmpty()) {
            entered.clear();
            stillChanged = true;
            carriedStale = true;
        }
        if (carriedStale) {
            carriedStale = false;
            rebuildCarried();
        }
        composed.clear();
        composedAt = Double.NaN;
    }

    public void live(Instance instance, CFrame local) {
        if (live.put(instance, local) == null && !moving.contains(instance)) stillChanged = true;
        composed.clear();
    }

    public void unlive(Instance instance) {
        if (live.remove(instance) == null) return;
        composed.clear();
        if (!moving.contains(instance)) stillChanged = true;
    }

    public CFrame sample(Instance instance, double alpha) {
        if (alpha != composedAt) {
            composed.clear();
            composedAt = alpha;
        }
        return compose(instance, alpha);
    }

    public CFrame sample(Instance instance) {
        return sample(instance, 1.0);
    }

    private CFrame compose(Instance instance, double alpha) {
        CFrame known = composed.get(instance);
        if (known != null) return known;
        CFrame local = localAt(instance, alpha);
        Instance parent = instance.parent();
        while (parent != null && !(parent instanceof Spatial)) parent = parent.parent();
        CFrame world = parent == null ? local : compose(parent, alpha).mul(local);
        composed.put(instance, world);
        return world;
    }

    private CFrame localAt(Instance instance, double alpha) {
        CFrame now = live.isEmpty() ? null : live.get(instance);
        if (now != null) return now;
        Track track = tracks.get(instance);
        return track == null ? Transforms.local(instance) : (CFrame) track.sampleAt(alpha);
    }

    private void adopt(Instance instance) {
        if (instance instanceof Spatial && !tracks.containsKey(instance)) {
            tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.local(instance)));
            stillChanged = true;
        }
        for (Instance child : instance.children()) adopt(child);
    }

    private void prune() {
        for (Iterator<Map.Entry<Instance, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
            Instance instance = it.next().getKey();
            if (!instance.isAlive()) {
                it.remove();
                live.remove(instance);
                stepping.remove(instance);
                moving.remove(instance);
                stillChanged = true;
            }
        }
    }

    private void write(Instance instance) {
        Track track = tracks.get(instance);
        CFrame local = Transforms.local(instance);
        if (track == null) {
            tracks.put(instance, new Track(PropertyType.CFRAME, local));
        } else {
            track.to(local);
        }
        if (stepping.add(instance)) entered.add(instance);
    }

    private long[] masks(ClassDef<?> def) {
        long frame = 0;
        long look = 0;
        for (PropertyDef property : def.properties()) {
            String name = property.name();
            long bit = 1L << property.index();
            if (name.equals("cframe") || name.equals("pivot") || name.equals("transform")) frame |= bit;
            if (looks.contains(name)) look |= bit;
        }
        return new long[] {frame, look};
    }

    private static boolean holdsSpatial(Instance instance) {
        if (instance instanceof Spatial) return true;
        for (Instance child : instance.children()) {
            if (holdsSpatial(child)) return true;
        }
        return false;
    }

    private void rebuildCarried() {
        moving.clear();
        for (Instance instance : stepping) markSubtreeMoving(instance);
    }

    private void markSubtreeMoving(Instance instance) {
        if (instance instanceof Spatial) moving.add(instance);
        for (Instance child : instance.children()) markSubtreeMoving(child);
    }
}
