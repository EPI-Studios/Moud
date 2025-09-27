package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

/**
 * Transform class that combines position, rotation, and scale
 * Useful for 3D object transformations
 */
public class Transform {
    @HostAccess.Export
    public Vector3 position;
    @HostAccess.Export
    public Quaternion rotation;
    @HostAccess.Export
    public Vector3 scale;

    public Transform() {
        this.position = Vector3.zero();
        this.rotation = Quaternion.identity();
        this.scale = Vector3.one();
    }

    public Transform(Vector3 position, Quaternion rotation, Vector3 scale) {
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
    }

    public Transform(Transform other) {
        this.position = new Vector3(other.position);
        this.rotation = new Quaternion(other.rotation);
        this.scale = new Vector3(other.scale);
    }

    @HostAccess.Export
    public static Transform identity() {
        return new Transform();
    }

    @HostAccess.Export
    public Matrix4 toMatrix() {
        return Matrix4.trs(position, rotation, scale);
    }

    @HostAccess.Export
    public Transform multiply(Transform other) {
        Vector3 newPosition = position.add(rotation.rotate(other.position.multiply(scale)));
        Quaternion newRotation = rotation.multiply(other.rotation);
        Vector3 newScale = scale.multiply(other.scale);
        return new Transform(newPosition, newRotation, newScale);
    }

    @HostAccess.Export
    public Vector3 transformPoint(Vector3 point) {
        return position.add(rotation.rotate(point.multiply(scale)));
    }

    @HostAccess.Export
    public Vector3 transformDirection(Vector3 direction) {
        return rotation.rotate(direction);
    }

    @HostAccess.Export
    public Vector3 inverseTransformPoint(Vector3 point) {
        return rotation.conjugate().rotate(point.subtract(position)).divide(scale);
    }

    @HostAccess.Export
    public Vector3 inverseTransformDirection(Vector3 direction) {
        return rotation.conjugate().rotate(direction);
    }

    @HostAccess.Export
    public Transform inverse() {
        Quaternion invRotation = rotation.conjugate();
        Vector3 invScale = new Vector3(1.0f / scale.x, 1.0f / scale.y, 1.0f / scale.z);
        Vector3 invPosition = invRotation.rotate(position.negate()).multiply(invScale);
        return new Transform(invPosition, invRotation, invScale);
    }

    @HostAccess.Export
    public Transform lerp(Transform target, float t) {
        return new Transform(
                position.lerp(target.position, t),
                rotation.lerp(target.rotation, t),
                scale.lerp(target.scale, t)
        );
    }

    @HostAccess.Export
    public Transform slerp(Transform target, float t) {
        return new Transform(
                position.lerp(target.position, t),
                rotation.slerp(target.rotation, t),
                scale.lerp(target.scale, t)
        );
    }

    @HostAccess.Export
    public Vector3 getForward() {
        return rotation.rotate(Vector3.forward());
    }

    @HostAccess.Export
    public Vector3 getRight() {
        return rotation.rotate(Vector3.right());
    }

    @HostAccess.Export
    public Vector3 getUp() {
        return rotation.rotate(Vector3.up());
    }

    @HostAccess.Export
    public void lookAt(Vector3 target, Vector3 up) {
        Vector3 forward = target.subtract(position).normalize();
        this.rotation = Quaternion.lookRotation(forward, up);
    }

    @HostAccess.Export
    public void translate(Vector3 translation) {
        this.position = this.position.add(translation);
    }

    @HostAccess.Export
    public void rotate(Quaternion rotation) {
        this.rotation = this.rotation.multiply(rotation);
    }

    @HostAccess.Export
    public void rotateAround(Vector3 point, Vector3 axis, float angle) {
        Vector3 toPoint = position.subtract(point);
        Quaternion rot = Quaternion.fromAxisAngle(axis, angle);
        position = point.add(rot.rotate(toPoint));
        rotation = rot.multiply(rotation);
    }

    @Override
    public String toString() {
        return String.format("Transform(pos: %s, rot: %s, scale: %s)",
                position.toString(), rotation.toString(), scale.toString());
    }
}