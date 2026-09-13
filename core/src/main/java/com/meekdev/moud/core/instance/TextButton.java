package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;

public final class TextButton extends TextLabel {

    public final Signal<Instance> activated = new Signal<>();

    public boolean autoButtonColor = true;
}
