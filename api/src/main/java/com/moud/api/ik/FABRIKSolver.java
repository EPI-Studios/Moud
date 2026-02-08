package com.moud.api.ik;

import com.moud.api.math.MathUtils;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import java.util.List;

public class FABRIKSolver implements IKSolver {

    @Override
    public boolean solve(IKChainState chain, Vector3 target, Vector3 rootPosition, IKChainDefinition definition) {
        List<Vector3> positions = chain.jointPositions;
        List<IKChainDefinition.JointDefinition> jointDefs = definition.joints;

        if (positions.size() < 2) {
            return false;
        }

        // calculate bone lengths
        float[] lengths = new float[jointDefs.size()];
        float totalLength = 0;
        for (int i = 0; i < jointDefs.size(); i++) {
            lengths[i] = jointDefs.get(i).length;
            totalLength += lengths[i];
        }

        chain.rootPosition = new Vector3(rootPosition);
        chain.targetPosition = new Vector3(target);

        // is it reachable?
        float distToTarget = rootPosition.distance(target);
        if (distToTarget > totalLength) {
            // target not reachable then stretch in that direction
            stretchTowardsTarget(positions, lengths, rootPosition, target);
            chain.targetReached = false;
            chain.distanceToTarget = positions.get(positions.size() - 1).distance(target);
            chain.iterationsUsed = 1;
            updateRotations(chain, jointDefs);
            return false;
        }

        // loop
        int iteration = 0;
        float tolerance = definition.tolerance;
        int maxIterations = definition.iterations;

        positions.set(0, new Vector3(rootPosition));

        Vector3 currentEndDir = positions.get(positions.size() - 1).subtract(rootPosition);
        Vector3 targetDir = target.subtract(rootPosition);
        float dotProduct = currentEndDir.lengthSquared() > MathUtils.EPSILON && targetDir.lengthSquared() > MathUtils.EPSILON
                ? currentEndDir.normalize().dot(targetDir.normalize())
                : 0;

        // if the chain is pointing in very wrong direction it reinitialize it
        if (dotProduct < 0.3f || currentEndDir.lengthSquared() < MathUtils.EPSILON) {
            initializePositions(positions, lengths, rootPosition, target);
        }

        while (iteration < maxIterations) {
            float endEffectorDist = positions.get(positions.size() - 1).distance(target);

            if (endEffectorDist < tolerance) {
                chain.targetReached = true;
                chain.distanceToTarget = endEffectorDist;
                chain.iterationsUsed = iteration;
                updateRotations(chain, jointDefs);
                return true;
            }

            backwardPass(positions, lengths, target, jointDefs);

            forwardPass(positions, lengths, rootPosition, jointDefs);

            iteration++;
        }

        chain.targetReached = false;
        chain.distanceToTarget = positions.get(positions.size() - 1).distance(target);
        chain.iterationsUsed = iteration;
        updateRotations(chain, jointDefs);

        return false;
    }

    private void initializePositions(List<Vector3> positions, float[] lengths, Vector3 root, Vector3 target) {
        Vector3 direction = target.subtract(root).normalize();
        if (direction.lengthSquared() < MathUtils.EPSILON) {
            direction = Vector3.forward();
        }

        positions.set(0, new Vector3(root));
        for (int i = 0; i < lengths.length; i++) {
            Vector3 prev = positions.get(i);
            positions.set(i + 1, prev.add(direction.multiply(lengths[i])));
        }
    }

    private void stretchTowardsTarget(List<Vector3> positions, float[] lengths, Vector3 root, Vector3 target) {
        Vector3 direction = target.subtract(root).normalize();
        if (direction.lengthSquared() < MathUtils.EPSILON) {
            direction = Vector3.forward();
        }

        positions.set(0, new Vector3(root));
        for (int i = 0; i < lengths.length; i++) {
            Vector3 prev = positions.get(i);
            positions.set(i + 1, prev.add(direction.multiply(lengths[i])));
        }
    }

    private void backwardPass(List<Vector3> positions, float[] lengths, Vector3 target,
                              List<IKChainDefinition.JointDefinition> jointDefs) {
        positions.set(positions.size() - 1, new Vector3(target));

        for (int i = positions.size() - 2; i >= 0; i--) {
            Vector3 current = positions.get(i);
            Vector3 next = positions.get(i + 1);

            float boneLength = lengths[i];
            Vector3 direction = current.subtract(next).normalize();

            if (direction.lengthSquared() < MathUtils.EPSILON) {
                // if points are coincident use the default direction (aka the pole vector)
                direction = getPoleDirection(jointDefs, i, Vector3.up());
            }

            if (i < jointDefs.size() && jointDefs.get(i).constraints != null) {
                IKConstraints constraints = jointDefs.get(i).constraints;
                if (constraints.poleVector != null) {
                    direction = applyPoleVector(direction, constraints.poleVector, 0.3f);
                }
            }

            positions.set(i, next.add(direction.multiply(boneLength)));
        }
    }


    private void forwardPass(List<Vector3> positions, float[] lengths, Vector3 root,
                             List<IKChainDefinition.JointDefinition> jointDefs) {
        positions.set(0, new Vector3(root));

        for (int i = 0; i < lengths.length; i++) {
            Vector3 current = positions.get(i);
            Vector3 next = positions.get(i + 1);

            float boneLength = lengths[i];
            Vector3 direction = next.subtract(current).normalize();

            if (direction.lengthSquared() < MathUtils.EPSILON) {
                direction = getPoleDirection(jointDefs, i, Vector3.forward());
            }

            if (i < jointDefs.size() && jointDefs.get(i).constraints != null) {
                IKConstraints constraints = jointDefs.get(i).constraints;
                direction = applyConstraints(direction, constraints, i > 0 ? positions.get(i - 1) : null, current);
            }

            positions.set(i + 1, current.add(direction.multiply(boneLength)));
        }
    }


    private Vector3 getPoleDirection(List<IKChainDefinition.JointDefinition> jointDefs, int index, Vector3 defaultDir) {
        if (index < jointDefs.size() && jointDefs.get(index).constraints != null) {
            IKConstraints constraints = jointDefs.get(index).constraints;
            if (constraints.poleVector != null) {
                return constraints.poleVector.normalize();
            }
        }
        return defaultDir;
    }


    private Vector3 applyPoleVector(Vector3 direction, Vector3 poleVector, float influence) {
        Vector3 normalizedPole = poleVector.normalize();
        Vector3 projectedPole = normalizedPole.reject(direction).normalize();

        if (projectedPole.lengthSquared() < MathUtils.EPSILON) {
            return direction;
        }

        return direction.add(projectedPole.multiply(influence)).normalize();
    }

    // uh the constraints are in WORLD SPACE so it doesn't work well for limbs at arbitrary angles
    // whoever read this, please use pole vectors
    private Vector3 applyConstraints(Vector3 direction, IKConstraints constraints,
                                     Vector3 grandparent, Vector3 parent) {
        if (constraints.minPitch == null && constraints.maxPitch == null &&
                constraints.minYaw == null && constraints.maxYaw == null) {
            return direction;
        }

        Vector3 localDir = direction;

        // pitch constraint (rotation around X axis and up/down)
        if (constraints.minPitch != null && constraints.maxPitch != null) {
            float pitch = (float) Math.asin(MathUtils.clamp(localDir.y, -1, 1));
            pitch = MathUtils.clamp(pitch, constraints.minPitch, constraints.maxPitch);
            float horizontalLength = (float) Math.sqrt(localDir.x * localDir.x + localDir.z * localDir.z);
            if (horizontalLength > MathUtils.EPSILON) {
                float newY = (float) Math.sin(pitch);
                float scale = (float) Math.cos(pitch) / horizontalLength;
                localDir = new Vector3(localDir.x * scale, newY, localDir.z * scale);
            }
        }

        // yaw constraint (rotation around Y axis and left/right)
        if (constraints.minYaw != null && constraints.maxYaw != null) {
            float yaw = (float) Math.atan2(localDir.x, localDir.z);
            yaw = MathUtils.clamp(yaw, constraints.minYaw, constraints.maxYaw);
            float horizontalLength = (float) Math.sqrt(localDir.x * localDir.x + localDir.z * localDir.z);
            localDir = new Vector3(
                    (float) Math.sin(yaw) * horizontalLength,
                    localDir.y,
                    (float) Math.cos(yaw) * horizontalLength
            );
        }

        return localDir.normalize();
    }

    private void updateRotations(IKChainState chain, List<IKChainDefinition.JointDefinition> jointDefs) {
        List<Vector3> positions = chain.jointPositions;
        List<Quaternion> rotations = chain.jointRotations;

        Vector3 chainUpHint = computeChainUpHint(positions, jointDefs);

        for (int i = 0; i < positions.size() - 1; i++) {
            Vector3 current = positions.get(i);
            Vector3 next = positions.get(i + 1);
            Vector3 boneDir = next.subtract(current);
            float boneLen = boneDir.length();

            if (boneLen < MathUtils.EPSILON) {
                rotations.set(i, Quaternion.identity());
                continue;
            }

            boneDir = boneDir.multiply(1.0f / boneLen);

            Vector3 upHint = chainUpHint;

            if (i < jointDefs.size() && jointDefs.get(i).constraints != null) {
                IKConstraints constraints = jointDefs.get(i).constraints;
                if (constraints.poleVector != null) {
                    Vector3 poleDir = constraints.poleVector.reject(boneDir);
                    if (poleDir.lengthSquared() > MathUtils.EPSILON) {
                        upHint = poleDir.normalize();
                    }
                }
            }

            Quaternion newRotation = Quaternion.lookRotation(boneDir, upHint);


            Quaternion prevRotation = rotations.get(i);
            if (prevRotation != null && newRotation.dot(prevRotation) < 0) {
                newRotation = newRotation.scale(-1.0f);
            }

            rotations.set(i, newRotation);
        }

        if (rotations.size() > 1) {
            rotations.set(rotations.size() - 1, rotations.get(rotations.size() - 2));
        }
    }

    private Vector3 computeChainUpHint(List<Vector3> positions, List<IKChainDefinition.JointDefinition> jointDefs) {
        if (!jointDefs.isEmpty() && jointDefs.get(0).constraints != null) {
            IKConstraints constraints = jointDefs.get(0).constraints;
            if (constraints.poleVector != null) {
                return constraints.poleVector.normalize();
            }
        }

        if (positions.size() >= 3) {
            Vector3 v1 = positions.get(1).subtract(positions.get(0));
            Vector3 v2 = positions.get(2).subtract(positions.get(0));
            Vector3 normal = v1.cross(v2);
            if (normal.lengthSquared() > MathUtils.EPSILON) {
                return normal.normalize();
            }
        }

        return Vector3.up();
    }

    @Override
    public IKSolverType getType() {
        return IKSolverType.FABRIK;
    }
}
