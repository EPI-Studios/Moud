package com.moud.plugin.api.entity;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

public interface GameObject {
    long id();

    Vector3 position();

    Quaternion rotation();

    GameObject teleport(Vector3 position);

    GameObject teleport(double x, double y, double z);

    GameObject rotate(Quaternion rotation);

    GameObject scale(Vector3 scale);

    GameObject scale(float uniform);

    void remove();
}
