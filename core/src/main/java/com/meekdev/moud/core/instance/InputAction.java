package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;

// a named action a client can press, bound to keys. input:down asks for it by this instance's name
public final class InputAction extends Instance {

    // comma separated: letters and digits as themselves, and names like Space, LeftShift, Enter, Tab,
    // Escape, Up, F1, MouseButton1, MouseButton2, MouseButton3
    public String keys = "";

    public boolean enabled = true;

    // fire on the client that pressed it, and never while a screen like chat has the keyboard
    public final Signal<Instance> began = new Signal<>();
    public final Signal<Instance> ended = new Signal<>();
}
