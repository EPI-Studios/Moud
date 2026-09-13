package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

public interface DebugRef {

    void line(Vec3 from, Vec3 to, Color color, double seconds);

    void box(CFrame frame, Vec3 size, Color color, double seconds);

    void sphere(Vec3 centre, double radius, Color color, double seconds);

    void label(Vec3 at, String text, Color color, double seconds);

    void watch(String name, String value);

    void clear();
}
