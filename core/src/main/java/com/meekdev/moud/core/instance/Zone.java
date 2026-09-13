package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Vec3;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// a region that knows what is inside it, with entered and left
//
// cheaper and steadier than touched: it asks once a tick whether a point is inside a shape, rather than
// whether two boxes overlap, and a body standing still on its edge does not flicker in and out
public final class Zone extends Spatial {

    public boolean enabled = true;

    public ZoneShape shape = ZoneShape.BOX;

    // the box, or for a sphere the diameter in x, or for a cylinder the diameter in x and height in y
    public Vec3 size = new Vec3(8, 4, 8);

    // what it watches: bodies players wear, instances with a tag, and instances of a class
    public boolean trackPlayers = true;
    public String trackTag = "";
    public String trackClass = "";

    // how long after something enters or leaves before it can do so again, in seconds
    @Prop(min = 0) public double cooldown;

    // which zone wins where zones overlap; the highest, and among equals the smallest
    public int priority;

    // a player inside is in this channel, and leaves it on the way out
    public Instance textChannel;

    // plays on the client of a player inside, looped
    @Prop(asset = true) public String soundId = "";
    @Prop(min = 0) public double volume = 1;

    public final Signal<Instance> entered = new Signal<>();
    public final Signal<Instance> left = new Signal<>();

    // what is inside right now, and when each last came or went
    final Set<Instance> inside = new LinkedHashSet<>();
    final Map<Instance, Double> changedAt = new HashMap<>();

    public Set<Instance> occupants() {
        return inside;
    }
}
