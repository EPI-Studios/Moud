package com.moud.api.ik;

import com.moud.api.math.Vector3;
import org.graalvm.polyglot.HostAccess;

/**
 * Defines angular constraints for an IK joint.
 * Constraints limit how far a joint can rotate in each direction.
 */
public class IKConstraints {
    /**
     * Minimum rotation angle (in radians) around each axis.
     * null means no lower limit.
     */
    @HostAccess.Export
    public Float minPitch;  // X-axis rotation

    @HostAccess.Export
    public Float maxPitch;

    @HostAccess.Export
    public Float minYaw;    // Y-axis rotation

    @HostAccess.Export
    public Float maxYaw;

    @HostAccess.Export
    public Float minRoll;   // Z-axis rotation

    @HostAccess.Export
    public Float maxRoll;

    /**
     * The preferred axis for this joint to bend along.
     */
    @HostAccess.Export
    public Vector3 poleVector;

    /**
     * Stiffness of this joint (0-1). Higher values make the joint resist movement.
     */
    @HostAccess.Export
    public float stiffness = 0.0f;

    public IKConstraints() {
        this.poleVector = null;
    }

    /**
     * Creates constraints with symmetric limits for pitch and yaw.
     */
    @HostAccess.Export
    public static IKConstraints symmetric(float pitchLimit, float yawLimit) {
        IKConstraints c = new IKConstraints();
        c.minPitch = -pitchLimit;
        c.maxPitch = pitchLimit;
        c.minYaw = -yawLimit;
        c.maxYaw = yawLimit;
        return c;
    }

    /**
     * Creates constraints for a hinge joint (like an elbow or knee).
     * Only allows rotation along one axis.
     */
    @HostAccess.Export
    public static IKConstraints hinge(float minAngle, float maxAngle, Vector3 axis) {
        IKConstraints c = new IKConstraints();
        // Determine which axis based on the hinge axis
        if (Math.abs(axis.x) > Math.abs(axis.y) && Math.abs(axis.x) > Math.abs(axis.z)) {
            c.minPitch = minAngle;
            c.maxPitch = maxAngle;
            c.minYaw = 0f;
            c.maxYaw = 0f;
            c.minRoll = 0f;
            c.maxRoll = 0f;
        } else if (Math.abs(axis.y) > Math.abs(axis.z)) {
            c.minYaw = minAngle;
            c.maxYaw = maxAngle;
            c.minPitch = 0f;
            c.maxPitch = 0f;
            c.minRoll = 0f;
            c.maxRoll = 0f;
        } else {
            c.minRoll = minAngle;
            c.maxRoll = maxAngle;
            c.minPitch = 0f;
            c.maxPitch = 0f;
            c.minYaw = 0f;
            c.maxYaw = 0f;
        }
        c.poleVector = axis;
        return c;
    }

    /**
     * Creates constraints for a ball joint (like a shoulder or hip).
     * Allows rotation in all directions within a cone.
     */
    @HostAccess.Export
    public static IKConstraints ballJoint(float coneAngle) {
        IKConstraints c = new IKConstraints();
        c.minPitch = -coneAngle;
        c.maxPitch = coneAngle;
        c.minYaw = -coneAngle;
        c.maxYaw = coneAngle;
        c.minRoll = (float) -Math.PI;
        c.maxRoll = (float) Math.PI;
        return c;
    }

    @HostAccess.Export
    public IKConstraints setPoleVector(Vector3 poleVector) {
        this.poleVector = poleVector;
        return this;
    }

    @HostAccess.Export
    public IKConstraints setStiffness(float stiffness) {
        this.stiffness = Math.max(0, Math.min(1, stiffness));
        return this;
    }

    /**
     * Creates a copy of these constraints.
     */
    @HostAccess.Export
    public IKConstraints copy() {
        IKConstraints copy = new IKConstraints();
        copy.minPitch = this.minPitch;
        copy.maxPitch = this.maxPitch;
        copy.minYaw = this.minYaw;
        copy.maxYaw = this.maxYaw;
        copy.minRoll = this.minRoll;
        copy.maxRoll = this.maxRoll;
        copy.stiffness = this.stiffness;
        if (this.poleVector != null) {
            copy.poleVector = new Vector3(this.poleVector);
        }
        return copy;
    }

    @Override
    public String toString() {
        return String.format("IKConstraints{pitch=[%.2f,%.2f], yaw=[%.2f,%.2f], stiffness=%.2f}",
                minPitch, maxPitch, minYaw, maxYaw, stiffness);
    }
}
