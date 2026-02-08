package com.moud.server.state;

import com.moud.api.math.Vector3;
import java.util.Objects;
import java.util.UUID;

public class LightState {

    private final UUID id;
    private Vector3 position;
    private int color;
    private float intensity;
    private float radius;

    public LightState(UUID id) {
        this.id = id;
        this.position = Vector3.zero();
        this.color = 0xFFFFFF; // White
        this.intensity = 1.0f;
        this.radius = 10.0f;
    }

    public UUID getId() {
        return id;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LightState that = (LightState) o;
        return color == that.color &&
                Float.compare(that.intensity, intensity) == 0 &&
                Float.compare(that.radius, radius) == 0 &&
                id.equals(that.id) &&
                position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, position, color, intensity, radius);
    }
}
