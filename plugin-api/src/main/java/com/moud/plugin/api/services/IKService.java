package com.moud.plugin.api.services;

import com.moud.api.ik.IKChainDefinition;
import com.moud.api.ik.IKChainState;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.ik.IKHandle;

import java.util.Collection;

/**
 * The registry and factory for the Inverse Kinematics system.
 */
public interface IKService {

    /**
     * Registers a new custom IK chain based on a definition.
     *
     * @param definition   The configuration object defining bone constraints, lengths, and the solver type.
     * @param rootPosition The initial world-space anchor for the chain's root.
     * @return A handle to the registered chain.
     */
    IKHandle createChain(IKChainDefinition definition, Vector3 rootPosition);

    /**
     * Factory method for a standard two-bone limb solver.
     *
     * @param id           The unique identifier for the new chain.
     * @param upperLength  Length of the proximal bone (e.g., thigh/humerus).
     * @param lowerLength  Length of the distal bone (e.g., shin/radius).
     * @param rootPosition The initial anchor position.
     * @param poleVector   The hint vector used to determine the bend plane (e.g., the direction the knee points).
     * @return A handle to the registered chain.
     */
    IKHandle createTwoBoneChain(String id, float upperLength, float lowerLength,
                                Vector3 rootPosition, Vector3 poleVector);

    /**
     * Factory method for a 3-segment procedural leg solver (e.g., Arachnids).
     * <p>
     * This variant relies on the solver's default heuristic for bending preferences.
     *
     * @param id           The unique identifier for the new chain.
     * @param coxaLength   Length of the base segment.
     * @param femurLength  Length of the mid segment.
     * @param tibiaLength  Length of the tip segment.
     * @param rootPosition The initial anchor position.
     * @return A handle to the registered chain.
     */
    IKHandle createSpiderLegChain(String id, float coxaLength, float femurLength,
                                  float tibiaLength, Vector3 rootPosition);

    /**
     * Factory method for a 3-segment procedural leg solver with explicit orientation.
     * <p>
     * Calculates the internal pole vector based on the provided {@code outwardDirection}
     * to ensure legs splay correctly relative to the body center.
     *
     * @param id               The unique identifier for the new chain.
     * @param coxaLength       Length of the base segment.
     * @param femurLength      Length of the mid segment.
     * @param tibiaLength      Length of the tip segment.
     * @param rootPosition     The initial anchor position.
     * @param outwardDirection A normalized vector indicating the "forward" or "outward" angle for this leg (on the XZ plane).
     * @return A handle to the registered chain.
     */
    IKHandle createSpiderLegChainWithPole(String id, float coxaLength, float femurLength,
                                          float tibiaLength, Vector3 rootPosition, Vector3 outwardDirection);

    /**
     * Factory method for a generic N-segment chain.
     *
     * @param id            The unique identifier for the new chain.
     * @param segmentCount  Total number of bones in the chain.
     * @param segmentLength Length of every individual bone.
     * @param rootPosition  The initial anchor position.
     * @return A handle to the registered chain.
     */
    IKHandle createUniformChain(String id, int segmentCount, float segmentLength, Vector3 rootPosition);

    /**
     * Retrieves an active chain handle by its ID.
     *
     * @param chainId The identifier to look up.
     * @return The associated {@link IKHandle}, or {@code null} if no such chain exists.
     */
    IKHandle getChain(String chainId);

    /**
     * Retrieves all currently registered IK chains.
     *
     * @return A collection of all active handles.
     */
    Collection<IKHandle> getAllChains();

    /**
     * Retrieves all chains associated with a specific model ID.
     *
     * @param modelId The internal model ID to filter by.
     * @return A collection of handles; returns an empty collection if none are found.
     */
    Collection<IKHandle> getChainsForModel(long modelId);

    /**
     * Retrieves all chains associated with a specific entity UUID.
     *
     * @param entityUuid The entity UUID string to filter by.
     * @return A collection of handles; returns an empty collection if none are found.
     */
    Collection<IKHandle> getChainsForEntity(String entityUuid);

    /**
     * Unregisters and destroys a specific chain.
     *
     * @param chainId The identifier of the chain to remove.
     * @return {@code true} if the chain existed and was removed; {@code false} otherwise.
     */
    boolean removeChain(String chainId);

    /**
     * Unregisters all chains associated with a specific model.
     *
     * @param modelId The target model ID.
     */
    void removeAllChainsForModel(long modelId);

    /**
     * Unregisters all chains associated with a specific entity.
     *
     * @param entityUuid The target entity UUID.
     */
    void removeAllChainsForEntity(String entityUuid);

    /**
     * Performs a stateless IK calculation.
     * <p>
     * This method computes the target pose immediately without registering a persistent {@link IKHandle}.
     *
     * @param definition     The chain configuration.
     * @param rootPosition   The world-space start of the chain.
     * @param targetPosition The desired world-space end-effector position.
     * @return The calculated state of the chain bones.
     */
    IKChainState solveOnce(IKChainDefinition definition, Vector3 rootPosition, Vector3 targetPosition);

    /**
     * Returns the current server tick.
     * <p>
     * Used primarily for timestamping IK states during synchronization.
     *
     * @return The current tick count.
     */
    long getCurrentTick();

    /**
     * Configures the global synchronization rate for IK updates.
     *
     * @param ticks The interval between network broadcasts (1 = every tick, 2 = every other tick, etc).
     */
    void setDefaultBroadcastRate(int ticks);
}