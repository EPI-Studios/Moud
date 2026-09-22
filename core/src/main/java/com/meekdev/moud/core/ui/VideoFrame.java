package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class VideoFrame extends GuiObject {

    @Prop(asset = true) public String video = "";
    public boolean playing;
    public boolean looped;
    @Prop(min = 0) public double volume = 1;
    @Prop(min = 0) public double timePosition;
    @Prop(readOnly = true, replicated = false, min = 0) public double timeLength;
    @Prop(readOnly = true, replicated = false) public boolean isLoaded;
    @Prop(readOnly = true, replicated = false) public Vector3 resolution = Vector3.ZERO;
    public Color videoColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double videoTransparency;

    public final Signal<Instance> played = new Signal<>();
    public final Signal<Instance> paused = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
    public final Signal<Instance> didLoop = new Signal<>();
    public final Signal<Instance> loaded = new Signal<>();
}
