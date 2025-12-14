package com.moud.api.ik;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import org.graalvm.polyglot.HostAccess;

/**
 * Represents a single joint in an IK chain.
 * Each joint has a position, rotation, and optional constraints.
 */
public class IKJoint {
    @HostAccess.Export
    public Vector3 position;

    @HostAccess.Export
    public Quaternion rotation;

    @HostAccess.Export
    public float length;

    @HostAccess.Export
    public IKConstraints constraints;

    /**
     * Optional name for identifying this joint (e.g., "shoulder", "elbow", "wrist").
     */
    @HostAccess.Export
    public String name;

    public IKJoint() {
        this.position = Vector3.zero();
        this.rotation = Quaternion.identity();
        this.length = 1.0f;
        this.constraints = null;
        this.name = null;
    }

    public IKJoint(Vector3 position, float length) {
        this.position = position;
        this.rotation = Quaternion.identity();
        this.length = length;
        this.constraints = null;
        this.name = null;
    }

    public IKJoint(Vector3 position, float length, String name) {
        this.position = position;
        this.rotation = Quaternion.identity();
        this.length = length;
        this.constraints = null;
        this.name = name;
    }

    @HostAccess.Export
    public IKJoint setConstraints(IKConstraints constraints) {
        this.constraints = constraints;
        return this;
    }

    @HostAccess.Export
    public IKJoint setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Creates a copy of this joint.
     */
    @HostAccess.Export
    public IKJoint copy() {
        IKJoint copy = new IKJoint(new Vector3(position), length, name);
        copy.rotation = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
        if (constraints != null) {
            copy.constraints = constraints.copy();
        }
        return copy;
    }

    @Override
    public String toString() {
        return String.format("IKJoint{name='%s', position=%s, length=%.3f}",
                name != null ? name : "unnamed", position, length);
    }
}
