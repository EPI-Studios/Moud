package com.moud.plugin.api.services.ik;

import com.moud.api.ik.IKChainDefinition;
import com.moud.api.ik.IKChainState;
import com.moud.api.ik.IKConstraints;
import com.moud.api.math.Vector3;

/**
 * Handle to an IK chain managed by the IK service.
 * Provides methods for updating targets, constraints, and retrieving state.
 */
public interface IKHandle extends AutoCloseable {
    /**
     * Gets the unique ID of this chain.
     */
    String getId();

    /**
     * Gets the chain definition.
     */
    IKChainDefinition getDefinition();

    /**
     * Gets the current state of the chain after the last solve.
     */
    IKChainState getState();

    /**
     * Sets the root position of the chain.
     *
     * @param position World-space position for the chain root
     */
    void setRootPosition(Vector3 position);

    /**
     * Gets the current root position.
     */
    Vector3 getRootPosition();

    /**
     * Sets the target position for the end effector.
     * The chain will solve towards this target on the next update.
     *
     * @param target World-space target position
     */
    void setTarget(Vector3 target);

    /**
     * Gets the current target position.
     */
    Vector3 getTarget();

    /**
     * Immediately solves the IK chain with current root and target.
     *
     * @return The updated chain state
     */
    IKChainState solve();

    /**
     * Solves the chain and broadcasts the result to connected clients.
     *
     * @return The updated chain state
     */
    IKChainState solveAndBroadcast();

    /**
     * Updates the pole vector for a specific joint.
     *
     * @param jointIndex Index of the joint
     * @param poleVector New pole vector
     */
    void setPoleVector(int jointIndex, Vector3 poleVector);

    /**
     * Updates constraints for a specific joint.
     *
     * @param jointIndex Index of the joint
     * @param constraints New constraints
     */
    void setJointConstraints(int jointIndex, IKConstraints constraints);

    /**
     * Enables or disables automatic solving each tick.
     * When enabled, the chain solves every server tick if the target has changed.
     *
     * @param enabled Whether to auto-solve
     */
    void setAutoSolve(boolean enabled);

    /**
     * Returns whether auto-solve is enabled.
     */
    boolean isAutoSolveEnabled();

    /**
     * Sets the interpolation factor for smooth target following.
     * 1.0 = instant, lower values = smoother but slower response.
     *
     * @param factor Interpolation factor (0.0 - 1.0)
     */
    void setInterpolationFactor(float factor);

    /**
     * Attaches this chain to a model.
     * The chain root will follow the model's position with an optional offset.
     *
     * @param modelId The model ID to attach to
     * @param offset Local offset from the model's position
     */
    void attachToModel(long modelId, Vector3 offset);

    /**
     * Attaches this chain to an entity (player or other).
     *
     * @param entityUuid UUID of the entity
     * @param offset Local offset from entity position
     */
    void attachToEntity(String entityUuid, Vector3 offset);

    /**
     * Detaches the chain from any attached model or entity.
     */
    void detach();

    /**
     * Gets the model ID this chain is attached to, or -1 if not attached.
     */
    long getAttachedModelId();

    /**
     * Gets the entity UUID this chain is attached to, or null if not attached.
     */
    String getAttachedEntityUuid();

    /**
     * Checks if the chain is currently attached to something.
     */
    boolean isAttached();

    /**
     * Removes this IK chain from the system.
     */
    void remove();

    @Override
    default void close() {
        remove();
    }
}
