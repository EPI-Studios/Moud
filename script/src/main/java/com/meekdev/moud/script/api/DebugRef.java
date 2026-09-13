package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public interface DebugRef {

    void line(Vector3 from, Vector3 to, Color color, double seconds);

    void box(CFrame frame, Vector3 size, Color color, double seconds);

    void sphere(Vector3 centre, double radius, Color color, double seconds);

    void label(Vector3 at, String text, Color color, double seconds);

    void watch(String name, String value);

    void clear();
}
