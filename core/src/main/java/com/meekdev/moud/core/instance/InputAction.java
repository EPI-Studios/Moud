package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;

public final class InputAction extends Instance {

    public String keys = "";

    public boolean enabled = true;

    public final Signal<Instance> began = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
}
