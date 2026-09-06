package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

// the render side of design 6.6: what gets drawn is a sample, never the live property
public final class Motion {

    private final Map<Instance, Track> tracks = new IdentityHashMap<>();

    // draining every frame is what makes the window the gap between two writes, so a value written
    // on the tick smooths across the frames after it and one written on the frame does not lag
    public void drain(InstanceTree tree, double dt) {
        for (Iterator<Map.Entry<Instance, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Instance, Track> entry = it.next();
            if (!entry.getKey().isAlive()) it.remove(); else entry.getValue().advance(dt);
        }

        // a moved parent changes every descendant's world frame while only the parent is dirty,
        // so the subtree has to follow or a child would render at a frame that no longer exists
        tree.drainDirty((instance, mask) -> {
            if (instance instanceof Spatial) writeSubtree(instance);
        });
    }

    private void writeSubtree(Instance instance) {
        if (instance instanceof Spatial) {
            CFrame world = Transforms.world(instance);
            Track track = tracks.get(instance);
            if (track == null) {
                tracks.put(instance, new Track(PropertyType.CFRAME, world));
            } else {
                track.write(world);
            }
        }
        for (Instance child : instance.children()) writeSubtree(child);
    }

    public CFrame sample(Instance instance) {
        Track track = tracks.get(instance);
        return track == null ? Transforms.world(instance) : (CFrame) track.sample();
    }
}
