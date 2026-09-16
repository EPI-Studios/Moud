package com.meekdev.moud.script.api;

import java.util.List;

public interface ControlsRef {

    List<String> NAMES = List.of("move", "jump", "look");

    boolean enabled(String control);

    void enabled(String control, boolean on);
}
