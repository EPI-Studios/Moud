package com.moud.api.ik;

import com.moud.api.math.MathUtils;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import java.util.List;

/**
 * Two-Bone IK Solver
 */
public class TwoBoneSolver implements IKSolver {

    @Override
    public boolean solve(IKChainState chain, Vector3 target, Vector3 rootPosition, IKChainDefinition definition) {
        List<Vector3> positions = chain.jointPositions;
        List<Quaternion> rotations = chain.jointRotations;
        List<IKChainDefinition.JointDefinition> jointDefs = definition.joints;

        if (positions.size() != 3 || jointDefs.size() != 2) {
            return new FABRIKSolver().solve(chain, target, rootPosition, definition);
        }

        float upperLength = jointDefs.get(0).length;
        float lowerLength = jointDefs.get(1).length;
        float totalLength = upperLength + lowerLength;

        chain.rootPosition = new Vector3(rootPosition);
        chain.targetPosition = new Vector3(target);

        Vector3 rootToTarget = target.subtract(rootPosition);
        float distToTarget = rootToTarget.length();

        Vector3 poleVector = getPoleVector(jointDefs);

        Vector3 midPosition;
        boolean targetReached;

        if (distToTarget >= totalLength - MathUtils.EPSILON) {
            Vector3 direction = rootToTarget.normalize();
            if (direction.lengthSquared() < MathUtils.EPSILON) {
                direction = Vector3.forward();
            }

            positions.set(0, new Vector3(rootPosition));
            positions.set(1, rootPosition.add(direction.multiply(upperLength)));
            positions.set(2, rootPosition.add(direction.multiply(totalLength)));

            targetReached = distToTarget <= totalLength + definition.tolerance;

        } else if (distToTarget < Math.abs(upperLength - lowerLength) + MathUtils.EPSILON) {
            Vector3 direction = rootToTarget.lengthSquared() > MathUtils.EPSILON ?
                    rootToTarget.normalize() : Vector3.forward();

            Vector3 foldDir = poleVector.reject(direction).normalize();
            if (foldDir.lengthSquared() < MathUtils.EPSILON) {
                foldDir = Vector3.up();
            }

            positions.set(0, new Vector3(rootPosition));
            positions.set(1, rootPosition.add(direction.multiply(upperLength * 0.5f)).add(foldDir.multiply(upperLength * 0.5f)));
            positions.set(2, new Vector3(target));

            targetReached = true;

        } else {
            midPosition = calculateMidPosition(rootPosition, target, upperLength, lowerLength, poleVector);

            positions.set(0, new Vector3(rootPosition));
            positions.set(1, midPosition);
            positions.set(2, new Vector3(target));

            targetReached = true;
        }

        updateRotations(chain);

        chain.targetReached = targetReached;
        chain.distanceToTarget = positions.get(2).distance(target);
        chain.iterationsUsed = 1;

        return targetReached;
    }

    /**
     * Calculate the middle joint position using cosines.
     */
    private Vector3 calculateMidPosition(Vector3 root, Vector3 target, float upperLen, float lowerLen, Vector3 poleVector) {
        Vector3 rootToTarget = target.subtract(root);
        float dist = rootToTarget.length();

        if (dist < MathUtils.EPSILON) {
            return root.add(poleVector.normalize().multiply(upperLen));
        }

        // find the angle at the root joint
        float cosAngleAtRoot = (dist * dist + upperLen * upperLen - lowerLen * lowerLen)
                / (2.0f * dist * upperLen);

        cosAngleAtRoot = MathUtils.clamp(cosAngleAtRoot, -1.0f, 1.0f);
        float sinAngleAtRoot = (float) Math.sqrt(1.0f - cosAngleAtRoot * cosAngleAtRoot);

        // direction from root to target
        Vector3 direction = rootToTarget.normalize();

        Vector3 bendAxis = poleVector.reject(direction);

        if (bendAxis.lengthSquared() < MathUtils.EPSILON) {
            bendAxis = getArbitraryPerpendicular(direction);
        }
        bendAxis = bendAxis.normalize();

        Vector3 alongDir = direction.multiply(upperLen * cosAngleAtRoot);
        Vector3 perpDir = bendAxis.multiply(upperLen * sinAngleAtRoot);

        return root.add(alongDir).add(perpDir);
    }

    /**
     * Get a vector perpendicular to the given direction.
     */
    private Vector3 getArbitraryPerpendicular(Vector3 direction) {
        Vector3 perp = direction.cross(Vector3.up());
        if (perp.lengthSquared() < MathUtils.EPSILON) {
            perp = direction.cross(Vector3.right());
        }
        return perp.normalize();
    }

    /**
     * Extract pole vector from joint definitions.
     */
    private Vector3 getPoleVector(List<IKChainDefinition.JointDefinition> jointDefs) {
        if (!jointDefs.isEmpty() && jointDefs.get(0).constraints != null) {
            IKConstraints constraints = jointDefs.get(0).constraints;
            if (constraints.poleVector != null) {
                return constraints.poleVector.normalize();
            }
        }

        if (jointDefs.size() > 1 && jointDefs.get(1).constraints != null) {
            IKConstraints constraints = jointDefs.get(1).constraints;
            if (constraints.poleVector != null) {
                return constraints.poleVector.normalize();
            }
        }

        return Vector3.up();
    }


    private void updateRotations(IKChainState chain) {
        List<Vector3> positions = chain.jointPositions;
        List<Quaternion> rotations = chain.jointRotations;

        Vector3 upperDir = positions.get(1).subtract(positions.get(0));
        float upperLen = upperDir.length();
        if (upperLen > MathUtils.EPSILON) {
            upperDir = upperDir.multiply(1.0f / upperLen);
            Vector3 upHint = computeUpHint(upperDir, positions);
            Quaternion newRot = Quaternion.lookRotation(upperDir, upHint);
            Quaternion prevRot = rotations.get(0);
            if (prevRot != null && newRot.dot(prevRot) < 0) {
                newRot = newRot.scale(-1.0f);
            }
            rotations.set(0, newRot);
        } else {
            rotations.set(0, Quaternion.identity());
        }

        Vector3 lowerDir = positions.get(2).subtract(positions.get(1));
        float lowerLen = lowerDir.length();
        if (lowerLen > MathUtils.EPSILON) {
            lowerDir = lowerDir.multiply(1.0f / lowerLen);
            Vector3 upHint = computeUpHint(lowerDir, positions);
            Quaternion newRot = Quaternion.lookRotation(lowerDir, upHint);
            Quaternion prevRot = rotations.get(1);
            if (prevRot != null && newRot.dot(prevRot) < 0) {
                newRot = newRot.scale(-1.0f);
            }
            rotations.set(1, newRot);
        } else {
            rotations.set(1, rotations.get(0));
        }

        rotations.set(2, rotations.get(1));
    }

    private Vector3 computeUpHint(Vector3 boneDir, List<Vector3> positions) {
        Vector3 v1 = positions.get(1).subtract(positions.get(0));
        Vector3 v2 = positions.get(2).subtract(positions.get(0));
        Vector3 planeNormal = v1.cross(v2);

        if (planeNormal.lengthSquared() > MathUtils.EPSILON) {
            return planeNormal.normalize();
        }

        Vector3 upCandidate = Vector3.up();
        if (Math.abs(boneDir.dot(upCandidate)) > 0.99f) {
            upCandidate = Vector3.forward();
        }
        return upCandidate;
    }

    @Override
    public IKSolverType getType() {
        return IKSolverType.TWO_BONE;
    }
}
