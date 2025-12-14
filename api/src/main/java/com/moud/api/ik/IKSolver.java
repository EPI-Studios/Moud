package com.moud.api.ik;

import com.moud.api.math.Vector3;

/**
 * Interface for IK solver implementations.
 * Each solver takes a chain state and target, then modifies the chain to reach the target.
 */
public interface IKSolver {
    /**
     * Solves the IK chain to reach the target position.
     *
     * @param chain        The chain state to modify (positions and rotations will be updated)
     * @param target       The target position to reach
     * @param rootPosition The fixed root position of the chain
     * @param definition   The chain definition with constraints and parameters
     * @return true if the target was reached within tolerance
     */
    boolean solve(IKChainState chain, Vector3 target, Vector3 rootPosition, IKChainDefinition definition);

    /**
     * Gets the type of this solver.
     */
    IKSolverType getType();
}
