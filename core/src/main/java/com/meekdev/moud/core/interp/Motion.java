package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class Motion {

    private final Map<Instance, Track> tracks = new IdentityHashMap<>();

    private final Set<Instance> stepping = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<Instance> moving = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<Instance, CFrame> composed = new IdentityHashMap<>();
    private double composedAt = Double.NaN;

    private long structure;
    private boolean stillChanged = true;
    private boolean carriedStale = true;

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
                if (stepping.remove(instance)) stillChanged = true;
            }
        });

        tree.drainDirty((instance, mask) -> {
            if (instance instanceof Spatial) write(instance);
        });

        for (Iterator<Instance> it = stepping.iterator(); it.hasNext(); ) {
            Track track = tracks.get(it.next());
            if (track == null || track.isStill()) {
                it.remove();
                stillChanged = true;
                carriedStale = true;
            }
        }
        if (carriedStale) {
            carriedStale = false;
            rebuildCarried();
        }
        composed.clear();
        composedAt = Double.NaN;
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
        CFrame world = parent == null ? local : compose(parent, alpha).mul(local);
        composed.put(instance, world);
        return world;
    }

    private CFrame localAt(Instance instance, double alpha) {
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
        if (stepping.add(instance)) {
            stillChanged = true;
            carriedStale = true;
        }
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
