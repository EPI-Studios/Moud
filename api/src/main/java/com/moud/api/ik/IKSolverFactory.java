package com.moud.api.ik;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory forIK solvers based their type.
 */
public class IKSolverFactory {
    private static final Map<IKSolverType, IKSolver> solvers = new HashMap<>();

    static {
        solvers.put(IKSolverType.FABRIK, new FABRIKSolver());
        solvers.put(IKSolverType.TWO_BONE, new TwoBoneSolver());
        // TODO : ccd need to be added
    }

    /**
     * Get a solver for the specified type.
     */
    public static IKSolver getSolver(IKSolverType type) {
        IKSolver solver = solvers.get(type);
        if (solver == null) {
            // Default to FABRIK
            return solvers.get(IKSolverType.FABRIK);
        }
        return solver;
    }

    /**
     * Get a solver for a chain definition.
     */
    public static IKSolver getSolver(IKChainDefinition definition) {
        return getSolver(definition.solverType);
    }

    /**
     * Register a custom solver implementation.
     */
    public static void registerSolver(IKSolverType type, IKSolver solver) {
        solvers.put(type, solver);
    }
}
