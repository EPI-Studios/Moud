package com.moud.api.ik;

import com.moud.api.math.Vector3;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;


public class IKChainDefinition {
    @HostAccess.Export
    public String id;

    @HostAccess.Export
    public List<JointDefinition> joints;

    @HostAccess.Export
    public IKSolverType solverType;

    /**
     * Number of iterations.
     */
    @HostAccess.Export
    public int iterations = 10;

    /**
     * Tolerance for distance from target.
     */
    @HostAccess.Export
    public float tolerance = 0.001f;

    /**
     * Whether to automatically orient joints to face the next joint.
     */
    @HostAccess.Export
    public boolean autoOrient = true;

    public IKChainDefinition() {
        this.joints = new ArrayList<>();
        this.solverType = IKSolverType.FABRIK;
    }

    public IKChainDefinition(String id) {
        this.id = id;
        this.joints = new ArrayList<>();
        this.solverType = IKSolverType.FABRIK;
    }

    /**
     * Creates a simple two-bone chain.
     */
    @HostAccess.Export
    public static IKChainDefinition twoBone(String id, float upperLength, float lowerLength, Vector3 poleVector) {
        IKChainDefinition def = new IKChainDefinition(id);
        def.solverType = IKSolverType.TWO_BONE;

        JointDefinition upper = new JointDefinition(upperLength);
        upper.name = "upper";
        upper.constraints = IKConstraints.ballJoint((float) Math.PI * 0.75f);
        if (poleVector != null) {
            upper.constraints.setPoleVector(poleVector);
        }

        JointDefinition lower = new JointDefinition(lowerLength);
        lower.name = "lower";
        lower.constraints = IKConstraints.hinge(0f, (float) Math.PI * 0.9f, Vector3.right());

        def.joints.add(upper);
        def.joints.add(lower);

        return def;
    }

    /**
     * Creates a spider leg chain (3 segments).
     */
    @HostAccess.Export
    public static IKChainDefinition spiderLeg(String id, float coxaLength, float femurLength, float tibiaLength) {
        IKChainDefinition def = new IKChainDefinition(id);
        def.solverType = IKSolverType.FABRIK;
        def.iterations = 10;

        // hip
        JointDefinition coxa = new JointDefinition(coxaLength);
        coxa.name = "coxa";
        coxa.constraints = new IKConstraints();
        coxa.constraints.setPoleVector(Vector3.up());

        // thigh
        JointDefinition femur = new JointDefinition(femurLength);
        femur.name = "femur";
        femur.constraints = new IKConstraints();
        femur.constraints.setPoleVector(Vector3.up());

        // shin
        JointDefinition tibia = new JointDefinition(tibiaLength);
        tibia.name = "tibia";
        tibia.constraints = new IKConstraints();
        tibia.constraints.setPoleVector(Vector3.up());

        def.joints.add(coxa);
        def.joints.add(femur);
        def.joints.add(tibia);

        return def;
    }

    /**
     * Creates a spider leg with a specific outward pole direction.
     * The pole vector determines which way the "knee" bends, outward and up for spider legs.
     *
     * @param id          Chain identifier
     * @param coxaLength  Length of the hip segment
     * @param femurLength Length of the upper leg segment
     * @param tibiaLength Length of the lower leg segment
     * @param outwardDir  Direction pointing away from body (normalized, typically (cos(angle), 0, sin(angle)))
     */
    @HostAccess.Export
    public static IKChainDefinition spiderLegWithPole(String id, float coxaLength, float femurLength,
                                                      float tibiaLength, Vector3 outwardDir) {
        IKChainDefinition def = spiderLeg(id, coxaLength, femurLength, tibiaLength);

        Vector3 poleVector = new Vector3(outwardDir.x, 1.5f, outwardDir.z).normalize();

        for (JointDefinition joint : def.joints) {
            if (joint.constraints == null) {
                joint.constraints = new IKConstraints();
            }
            joint.constraints.setPoleVector(poleVector);
        }

        return def;
    }

    /**
     * Adds a joint to the chain.
     */
    @HostAccess.Export
    public IKChainDefinition addJoint(JointDefinition joint) {
        joints.add(joint);
        return this;
    }

    /**
     * Adds a joint with just a length.
     */
    @HostAccess.Export
    public IKChainDefinition addJoint(float length) {
        joints.add(new JointDefinition(length));
        return this;
    }

    /**
     * Adds a named joint with a length.
     */
    @HostAccess.Export
    public IKChainDefinition addJoint(String name, float length) {
        JointDefinition joint = new JointDefinition(length);
        joint.name = name;
        joints.add(joint);
        return this;
    }

    @HostAccess.Export
    public IKChainDefinition setSolverType(IKSolverType type) {
        this.solverType = type;
        return this;
    }

    @HostAccess.Export
    public IKChainDefinition setIterations(int iterations) {
        this.iterations = iterations;
        return this;
    }

    @HostAccess.Export
    public IKChainDefinition setTolerance(float tolerance) {
        this.tolerance = tolerance;
        return this;
    }

    /**
     * Calculates the maximum extension of this chain.
     */
    @HostAccess.Export
    public float getTotalLength() {
        float total = 0;
        for (JointDefinition joint : joints) {
            total += joint.length;
        }
        return total;
    }

    @Override
    public String toString() {
        return String.format("IKChainDefinition{id='%s', joints=%d, solver=%s, totalLength=%.2f}",
                id, joints.size(), solverType, getTotalLength());
    }

    /**
     * Definition for a single joint in the chain.
     */
    public static class JointDefinition {
        @HostAccess.Export
        public String name;

        @HostAccess.Export
        public float length;

        @HostAccess.Export
        public IKConstraints constraints;

        /**
         * Local offset from parent joint.
         */
        @HostAccess.Export
        public Vector3 localOffset;

        public JointDefinition() {
            this.length = 1.0f;
        }

        public JointDefinition(float length) {
            this.length = length;
        }

        @HostAccess.Export
        public JointDefinition setName(String name) {
            this.name = name;
            return this;
        }

        @HostAccess.Export
        public JointDefinition setConstraints(IKConstraints constraints) {
            this.constraints = constraints;
            return this;
        }

        @HostAccess.Export
        public JointDefinition setLocalOffset(Vector3 offset) {
            this.localOffset = offset;
            return this;
        }
    }
}
