package com.moud.plugin.api.services.primitives;

/**
 * Types of primitive shapes that can be rendered.
 */
public enum PrimitiveType {
    /**
     * A unit cube centered at origin.
     */
    CUBE,

    /**
     * A unit sphere centered at origin.
     */
    SPHERE,

    /**
     * A cylinder along the Y axis.
     */
    CYLINDER,

    /**
     * A capsule (cylinder with hemispherical caps) along the Y axis.
     */
    CAPSULE,

    /**
     * A single line segment between two points.
     */
    LINE,

    /**
     * Connected line segments through multiple points.
     */
    LINE_STRIP,

    /**
     * A flat plane in the XZ plane.
     */
    PLANE,

    /**
     * A cone pointing along the Y axis.
     */
    CONE
}
