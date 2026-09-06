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

// the render side of design 6.6: what gets drawn is a sample, never the live property.
// per frame cost is proportional to what is moving, not to how much exists, which is 11.3
public final class Motion {

    private final Map<Instance, Track> tracks = new IdentityHashMap<>();
    private final Set<Instance> moving = Collections.newSetFromMap(new IdentityHashMap<>());

    private long structure;
    private boolean stillChanged = true;

    public Collection<Instance> moving() {
        return moving;
    }

    public boolean isMoving(Instance instance) {
        return moving.contains(instance);
    }

    // true once whenever the set of things standing still changed, so the static batch is
    // re-emitted then and never otherwise
    public boolean takeStillChanged() {
        boolean was = stillChanged;
        stillChanged = false;
        return was;
    }

    public void drain(InstanceTree tree, double dt) {
        // every spatial gets a track when it appears, not when it first moves. without this a
        // static part has no track and every frame recomposes its world frame from the parent
        // chain, which is a parent walk and three allocations per part per frame
        if (tree.structureEpoch() != structure) {
            structure = tree.structureEpoch();
            if (tree.root() != null) adopt(tree.root());
            prune();
        }

        // only what is in flight is advanced, so a million still parts cost nothing here
        for (Iterator<Instance> it = moving.iterator(); it.hasNext(); ) {
            Instance instance = it.next();
            Track track = tracks.get(instance);
            if (track == null) {
                it.remove();
                stillChanged = true;
                continue;
            }
            track.advance(dt);
            // kept a moment past settling: a part written every tick settles exactly as the next
            // write lands, and dropping it there would leave the next window at zero and snap
            if (track.settled() && track.sinceWrite() > Track.MAX_AUTO_WINDOW) {
                it.remove();
                stillChanged = true;
            }
        }

        // a moved parent changes every descendant's world frame while only the parent is dirty,
        // so the subtree has to follow or a child would render at a frame that no longer exists
        tree.drainDirty((instance, mask) -> {
            if (instance instanceof Spatial) writeSubtree(instance);
        });
    }

    public CFrame sample(Instance instance) {
        Track track = tracks.get(instance);
        return track == null ? Transforms.world(instance) : (CFrame) track.sample();
    }

    private void adopt(Instance instance) {
        if (instance instanceof Spatial && !tracks.containsKey(instance)) {
            tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.world(instance)));
            // in flight briefly so it accumulates a window, then it drains out to the static batch
            moving.add(instance);
            stillChanged = true;
        }
        for (Instance child : instance.children()) adopt(child);
    }

    private void prune() {
        for (Iterator<Map.Entry<Instance, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
            Instance instance = it.next().getKey();
            if (!instance.isAlive()) {
                it.remove();
                moving.remove(instance);
                stillChanged = true;
            }
        }
    }

    private void writeSubtree(Instance instance) {
        if (instance instanceof Spatial) {
            Track track = tracks.get(instance);
            if (track == null) {
                tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.world(instance)));
            } else {
                track.write(Transforms.world(instance));
            }
            if (moving.add(instance)) stillChanged = true;
        }
        for (Instance child : instance.children()) writeSubtree(child);
    }
}
