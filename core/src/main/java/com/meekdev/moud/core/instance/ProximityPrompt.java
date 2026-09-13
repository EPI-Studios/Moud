package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

// "press e to open": a prompt that shows when a player is close to what it is parented to, and fires when
// they press or hold its key
//
// the client shows it and reads the key; the server hears about it, checks the player really is close,
// and fires triggered there too, so a place handles it on whichever side it likes
public final class ProximityPrompt extends Instance {

    public boolean enabled = true;

    public String actionText = "Interact";
    public String objectText = "";

    // the keys that press it, like the keys of an InputAction
    public String keys = "e";

    // how long the key has to be held, in seconds. zero fires on the press
    @Prop(min = 0) public double holdDuration;

    @Prop(min = 0) public double maxActivationDistance = 10;

    // hidden when something solid is between the player and it
    public boolean requiresLineOfSight = true;

    // where it floats, from what it is parented to
    public Vec3 offset = new Vec3(0, 1, 0);

    public Color backgroundColor = new Color(0.05f, 0.05f, 0.07f, 1);
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.25;
    public Color textColor = Color.WHITE;
    public Color keyColor = Color.WHITE;

    // carry the body of the player it was about
    public final Signal<Instance> triggered = new Signal<>();
    public final Signal<Instance> holdBegan = new Signal<>();
    public final Signal<Instance> holdEnded = new Signal<>();

    // on the client, as it appears and goes for this player
    public final Signal<Instance> shown = new Signal<>();
    public final Signal<Instance> hidden = new Signal<>();
}
