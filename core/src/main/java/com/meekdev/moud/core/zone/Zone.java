package com.meekdev.moud.core.zone;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Vector3;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Zone extends Spatial {

    public boolean enabled = true;

    public ZoneShape shape = ZoneShape.BOX;

    public Vector3 size = new Vector3(8, 4, 8);

    public boolean trackPlayers = true;
    public String trackTag = "";
    public String trackClass = "";

    @Prop(min = 0) public double cooldown;

    public int priority;

    public Instance textChannel;

    @Prop(asset = true) public String soundId = "";
    @Prop(min = 0) public double volume = 1;

    public final Signal<Instance> entered = new Signal<>();
    public final Signal<Instance> left = new Signal<>();

    final Set<Instance> inside = new LinkedHashSet<>();
    final Map<Instance, Double> changedAt = new HashMap<>();

    public Set<Instance> occupants() {
        return inside;
    }
}
