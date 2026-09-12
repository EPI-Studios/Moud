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
//
// what is interpolated is a *local* frame, and the chain is composed when a frame asks for it
//
// it used to interpolate world frames, with a parent that moved rewriting the world frame of
// everything under it. that is a straight line through world space, and a straight line is only the
// right answer for something moving in a straight line. anything carried round -- a rider on a deck
// that turns, a lantern on a post on that deck -- travels an arc, and the straight line between two
// points of an arc is the chord: the error is zero at both ends of a tick and worst in the middle,
// so it appears and vanishes twenty times a second. no correction fixes that, because the thing
// being interpolated is wrong
//
// composed from local frames it comes out exact rather than corrected. a rotation interpolated as a
// rotation, applied to a local point that is not moving, *is* the arc -- there is no radius in it
// and no error term. it is also less work: a deck with five hundred parts on it wrote five hundred
// world frames a tick and now writes one, because none of their local frames changed
public final class Motion {

    private final Map<Instance, Track> tracks = new IdentityHashMap<>();

    // whose own local frame is in flight
    private final Set<Instance> stepping = Collections.newSetFromMap(new IdentityHashMap<>());

    // whose world frame is in flight, which is the above plus everything hanging off it
    //
    // the two are different questions and the batches want this one: a lantern bolted to a turning
    // deck never moves an inch of its own and is somewhere new every frame
    private final Set<Instance> moving = Collections.newSetFromMap(new IdentityHashMap<>());

    // one composed frame per instance per frame of video
    //
    // composing a chain is only affordable if a body that twelve limbs hang off is composed once
    // rather than twelve times. cleared when the tick advances or the fraction asked for changes,
    // which between them cover every way the answer can go stale
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
        // static part has no track and every frame recomposes its local frame from its properties
        if (tree.structureEpoch() != structure) {
            structure = tree.structureEpoch();
            if (tree.root() != null) adopt(tree.root());
            prune();
            // the shape of the tree decides who is carried by whom
            carriedStale = true;
        }

        // only what is in flight is stepped, so a million still parts cost nothing here
        for (Instance instance : stepping) {
            Track track = tracks.get(instance);
            if (track != null) track.beginLeg();
        }

        // a reparent writes no local frame at all: the tree leaves cframe alone, so what changed is
        // which chain the instance hangs from, and composing picks that up by itself. what it does
        // change is who is carried, which is the one thing that has to be rebuilt
        tree.drainMoved(id -> {
            Instance instance = tree.byId(id);
            if (instance == null) return;
            carriedStale = true;
            // a reparent restates where something hangs; it does not move it. the frame it held was
            // stated against the old parent and means nothing under the new one, so the leg is
            // reseated rather than interpolated -- interpolating it would glide the instance in from
            // wherever the old parent happened to be, which for a deck twenty blocks out is twenty
            // blocks of glide
            //
            // its descendants need nothing: their frames were always stated against their own
            // parents, and those did not change
            if (instance instanceof Spatial) {
                tracks.put(instance, new Track(PropertyType.CFRAME, Transforms.local(instance)));
                if (stepping.remove(instance)) stillChanged = true;
            }
        });

        // only the instance that was written. its descendants keep the local frames they had, and
        // their world frames follow from this one when something asks
        tree.drainDirty((instance, mask) -> {
            if (instance instanceof Spatial) write(instance);
        });

        for (Iterator<Instance> it = stepping.iterator(); it.hasNext(); ) {
            Track track = tracks.get(it.next());
            if (track == null || track.still()) {
                it.remove();
                stillChanged = true;
                carriedStale = true;
            }
        }
        if (carriedStale) {
            carriedStale = false;
            rebuildCarried();
        }
        // a new tick, so every frame of the last one is void whatever fraction it was asked at
        composed.clear();
        composedAt = Double.NaN;
    }

    // alpha is how far through the current tick the frame is
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

    // a folder has no frame of its own and passes its parent's through, which is what Transforms
    // does for the live value. an instance that has not been adopted yet reads live
    private CFrame localAt(Instance instance, double alpha) {
        Track track = tracks.get(instance);
        return track == null ? Transforms.local(instance) : (CFrame) track.sampleAt(alpha);
    }

    private void adopt(Instance instance) {
        if (instance instanceof Spatial && !tracks.containsKey(instance)) {
            // born still: both ends of its leg are where it is, so it belongs to the static batch
            // until something writes it
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

    // whose world frame is in flight, worked out from whose local frame is
    //
    // rebuilt when the moving set or the shape of the tree changes rather than every tick, so a deck
    // that has been turning for an hour costs nothing to keep track of
    private void rebuildCarried() {
        moving.clear();
        for (Instance instance : stepping) carry(instance);
    }

    private void carry(Instance instance) {
        if (instance instanceof Spatial) moving.add(instance);
        for (Instance child : instance.children()) carry(child);
    }
}
