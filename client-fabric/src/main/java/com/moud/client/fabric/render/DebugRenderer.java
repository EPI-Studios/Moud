package com.moud.client.fabric.render;

import org.joml.Vector3f;

public interface DebugRenderer {
    void line(Vector3f from, Vector3f to, int colorARGB, float thickness);
    void box(Vector3f min, Vector3f max, int colorARGB, float thickness);
    void frustum(Vector3f[] points, int colorARGB, float thickness);
    void sphere(Vector3f center, float radius, int colorARGB, int segments);
    void circle(Vector3f center, float radius, Vector3f normal, int colorARGB, int segments);
    void arc(Vector3f center, float radius, Vector3f normal, Vector3f startDir, float angleDeg, int colorARGB, int segments);
    void clear();
}
