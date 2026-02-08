package com.moud.api.ik;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of an IK chain after solving.
 * Contains positions and rotations for all joints.
 */
public class IKChainState {
    /**
     * Unique identifier for this chain.
     */
    @HostAccess.Export
    public String chainId;

    /**
     * The root position of the chain (first joint).
     */
    @HostAccess.Export
    public Vector3 rootPosition;

    /**
     * Current target position the chain is solving towards.
     */
    @HostAccess.Export
    public Vector3 targetPosition;

    /**
     * World-space positions of each joint, including the end.
     */
    @HostAccess.Export
    public List<Vector3> jointPositions;

    /**
     * World-space rotations of each joint.
     */
    @HostAccess.Export
    public List<Quaternion> jointRotations;

    /**
     * Whether the chain successfully reached the target within tolerance.
     */
    @HostAccess.Export
    public boolean targetReached;

    /**
     * Distance from the end effector to the target position.
     */
    @HostAccess.Export
    public float distanceToTarget;

    /**
     * Number of iterations the solver used.
     */
    @HostAccess.Export
    public int iterationsUsed;

    /**
     * Timestamp when this state was computed (server tick).
     */
    @HostAccess.Export
    public long timestamp;

    public IKChainState() {
        this.jointPositions = new ArrayList<>();
        this.jointRotations = new ArrayList<>();
    }

    public IKChainState(String chainId, int jointCount) {
        this.chainId = chainId;
        this.jointPositions = new ArrayList<>(jointCount);
        this.jointRotations = new ArrayList<>(jointCount);
        for (int i = 0; i < jointCount; i++) {
            jointPositions.add(Vector3.zero());
            jointRotations.add(Quaternion.identity());
        }
    }

    /**
     * Gets the position of the tip of the chain.
     */
    @HostAccess.Export
    public Vector3 getEndEffectorPosition() {
        if (jointPositions.isEmpty()) {
            return Vector3.zero();
        }
        return jointPositions.getLast();
    }

    /**
     * Gets the position of a specific joint by index.
     */
    @HostAccess.Export
    public Vector3 getJointPosition(int index) {
        if (index < 0 || index >= jointPositions.size()) {
            throw new IndexOutOfBoundsException("Joint index out of range: " + index);
        }
        return jointPositions.get(index);
    }

    /**
     * Gets the rotation of a specific joint by index.
     */
    @HostAccess.Export
    public Quaternion getJointRotation(int index) {
        if (index < 0 || index >= jointRotations.size()) {
            throw new IndexOutOfBoundsException("Joint index out of range: " + index);
        }
        return jointRotations.get(index);
    }

    /**
     * Gets the number of joints in this chain.
     */
    @HostAccess.Export
    public int getJointCount() {
        return jointPositions.size();
    }

    /**
     * Interpolates between this state and another state.
     */
    @HostAccess.Export
    public IKChainState lerp(IKChainState target, float t) {
        if (target.jointPositions.size() != this.jointPositions.size()) {
            throw new IllegalArgumentException("Cannot lerp chains with different joint counts");
        }

        IKChainState result = new IKChainState(chainId, jointPositions.size());
        result.rootPosition = this.rootPosition.lerp(target.rootPosition, t);
        result.targetPosition = this.targetPosition.lerp(target.targetPosition, t);

        for (int i = 0; i < jointPositions.size(); i++) {
            result.jointPositions.set(i, this.jointPositions.get(i).lerp(target.jointPositions.get(i), t));
            result.jointRotations.set(i, this.jointRotations.get(i).slerp(target.jointRotations.get(i), t));
        }

        result.targetReached = t > 0.5f ? target.targetReached : this.targetReached;
        result.distanceToTarget = (1 - t) * this.distanceToTarget + t * target.distanceToTarget;

        return result;
    }

    /**
     * Creates a deep copy of this state.
     */
    @HostAccess.Export
    public IKChainState copy() {
        IKChainState copy = new IKChainState(chainId, jointPositions.size());
        copy.rootPosition = new Vector3(rootPosition);
        copy.targetPosition = new Vector3(targetPosition);
        copy.targetReached = targetReached;
        copy.distanceToTarget = distanceToTarget;
        copy.iterationsUsed = iterationsUsed;
        copy.timestamp = timestamp;

        for (int i = 0; i < jointPositions.size(); i++) {
            copy.jointPositions.set(i, new Vector3(jointPositions.get(i)));
            Quaternion rot = jointRotations.get(i);
            copy.jointRotations.set(i, new Quaternion(rot.x, rot.y, rot.z, rot.w));
        }

        return copy;
    }

    @Override
    public String toString() {
        return String.format("IKChainState{chainId='%s', joints=%d, reached=%b, distance=%.4f}",
                chainId, jointPositions.size(), targetReached, distanceToTarget);
    }
}
