package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vector3;
import java.util.List;
import java.util.Map;

public interface DevicesRef {

    List<String> MOUSE_BEHAVIORS = List.of("default", "lockCenter", "lockCurrentPosition");

    record Event(String type, String key, String state, Vector3 position, Vector3 delta, boolean processed) {}

    record Gamepad(int index, Vector3 leftStick, Vector3 rightStick, double leftTrigger, double rightTrigger, Map<String, Boolean> buttons) {}

    boolean keyDown(String key);

    boolean mouseButtonDown(int button);

    List<String> keysDown();

    String keyCode(String key);

    String keyName(String key);

    String mouseBehavior();

    void mouseBehavior(String behavior);

    boolean mouseIconEnabled();

    void mouseIconEnabled(boolean on);

    List<Gamepad> gamepads();
}
