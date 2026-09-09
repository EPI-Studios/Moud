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

    // one tick of the stream, drained where the stream arrives rather than per frame
    //
    // last tick's value becomes the start of this tick's leg, whatever arrived becomes its end,
    // and anything nothing wrote settles. a frame then draws the leg at its own tick fraction, so
    // the render clock never decides how fast a part appears to move
    public void drain(InstanceTree tree) {
        // every spatial gets a track when it appears, not when it first moves. without this a
        // static part has no track and every frame recomposes its world frame from the parent
        // chain, which is a parent walk and three allocations per part per frame
        if (tree.structureEpoch() != structure) {
            structure = tree.structureEpoch();
            if (tree.root() != null) adopt(tree.root());
            prune();
        }

        // only what is in flight is stepped, so a million still parts cost nothing here
        for (Instance instance : moving) {
            Track track = tracks.get(instance);
            if (track != null) track.beginLeg();
        }

        // a reparent leaves every property alone and still moves the world frame of everything
        // under it, so it arrives on its own channel and the subtree is rewritten from here
        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance instanceof Spatial) writeSubtree(instance);
        });

        // a moved parent changes every descendant's world frame while only the parent is dirty,
        // so the subtree has to follow or a child would render at a frame that no longer exists
        tree.drainDirty((instance, mask) -> {
            if (instance instanceof Spatial) writeSubtree(instance);
        });

        for (Iterator<Instance> it = moving.iterator(); it.hasNext(); ) {
            Track track = tracks.get(it.next());
            if (track == null || track.still()) {
                it.remove();
                stillChanged = true;
            }
        }
    }

    // alpha is how far through the current tick the frame is
    public CFrame sample(Instance instance, double alpha) {
        Track track = tracks.get(instance);
        return track == null ? Transforms.world(instance) : (CFrame) track.sampleAt(alpha);
    }

    public CFrame sample(Instance instance) {
        return sample(instance, 1.0);
    }

    private void adopt(Instance instance) {
        if (instance instanceof Spatial && !tracks.containsKey(instance)) {
            // born still: both ends of its leg are where it is, so it belongs to the static batch
            // until something writes it
            tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.world(instance)));
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
                track.to(Transforms.world(instance));
            }
            if (moving.add(instance)) stillChanged = true;
        }
        for (Instance child : instance.children()) writeSubtree(child);
    }
}
