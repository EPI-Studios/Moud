package com.meekdev.moud.core.input;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class InputAction extends Instance {

    public String keys = "";

    public boolean enabled = true;

    public final Signal<Instance> began = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
}
