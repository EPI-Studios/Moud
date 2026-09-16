package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class AppWindow extends Instance {

    public String title = "";
    public double x = 100;
    public double y = 100;
    @Prop(min = 1) public double width = 400;
    @Prop(min = 1) public double height = 300;
    public boolean visible = true;
    public boolean decorated = true;
    public boolean transparent;
    public boolean alwaysOnTop;
    public boolean clickThrough;
    public boolean resizable = true;
    @Prop(min = 0, max = 1) public double opacity = 1;
    @Prop(driven = true) public boolean focused;

    public Instance gui;
    public Instance camera;
    public boolean mirror;
    public Instance clickable;

    public final Signal<Instance> closing = new Signal<>();
    public final Signal<Instance> moved = new Signal<>();
    public final Signal<Instance> resized = new Signal<>();

    private boolean closeKept;
    private boolean focusWanted;

    public void focus() {
        focusWanted = true;
    }

    public boolean takeFocusWanted() {
        boolean wanted = focusWanted;
        focusWanted = false;
        return wanted;
    }

    public void keepOpen() {
        closeKept = true;
    }

    public boolean takeKeptOpen() {
        boolean kept = closeKept;
        closeKept = false;
        return kept;
    }
}
