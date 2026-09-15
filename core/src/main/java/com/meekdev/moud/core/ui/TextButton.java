package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class TextButton extends TextLabel {

    public final Signal<Instance> activated = new Signal<>();
    public final Signal<Instance> mouseEnter = new Signal<>();
    public final Signal<Instance> mouseLeave = new Signal<>();

    @Prop(replicated = false) public boolean hovered;
    @Prop(replicated = false) public boolean pressed;

    public boolean autoButtonColor = true;
}
