package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class TextButton extends TextLabel {

    public final Signal<Instance> activated = new Signal<>();

    public boolean autoButtonColor = true;
}
