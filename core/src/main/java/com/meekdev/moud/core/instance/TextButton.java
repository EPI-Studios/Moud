package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;

public final class TextButton extends TextLabel {

    // fires on the client that clicked it
    public final Signal<Instance> activated = new Signal<>();

    // darkens under the pointer and while held
    public boolean autoButtonColor = true;
}
