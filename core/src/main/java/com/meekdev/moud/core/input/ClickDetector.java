package com.meekdev.moud.core.input;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class ClickDetector extends Instance {

    public record Clicker(String player) {}

    @Prop(min = 0) public double maxActivationDistance = 32;

    public String cursorIcon = "";

    public final Signal<Clicker> mouseClick = new Signal<>();
    public final Signal<Clicker> rightMouseClick = new Signal<>();
    public final Signal<Clicker> mouseHoverEnter = new Signal<>();
    public final Signal<Clicker> mouseHoverLeave = new Signal<>();
}
