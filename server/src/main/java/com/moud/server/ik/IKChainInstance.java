package com.moud.server.ik;

import com.moud.api.ik.*;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.ik.IKHandle;
import com.moud.server.entity.ModelManager;
import com.moud.server.proxy.ModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.UUID;

public class IKChainInstance implements IKHandle {
    private static final float TICK_DELTA_SECONDS = 0.05f;
    private final String id;
    private final IKChainDefinition definition;
    private final IKChainState state;
    private final IKSolver solver;
    private final IKServiceImpl service;
    private Vector3 rootPosition;
    private Vector3 targetPosition;
    private Vector3 currentSolverTarget;
    private Vector3 stepStartPosition;
    private float stepProgress = 1.0f;
    private boolean isStepping = false;
    private float stepHeight = 0.5f;
    private float stepDurationSeconds = 0.3f;
    private boolean autoSolve = false;
    private float interpolationFactor = 1.0f;
    private long attachedModelId = -1;
    private String attachedEntityUuid = null;
    private Vector3 attachOffset = Vector3.zero();
    private boolean dirty = false;
    private boolean removed = false;
    private long lastBroadcastTick = 0;

    public IKChainInstance(String id, IKChainDefinition definition, Vector3 rootPosition, IKServiceImpl service) {
        this.id = id;
        this.definition = definition;
        this.rootPosition = new Vector3(rootPosition);
        this.targetPosition = new Vector3(rootPosition);
        this.currentSolverTarget = new Vector3(rootPosition);
        this.stepStartPosition = new Vector3(rootPosition);
        this.service = service;
        int jointCount = definition.joints.size() + 1;
        this.state = new IKChainState(id, jointCount);
        this.state.rootPosition = new Vector3(rootPosition);
        this.state.targetPosition = new Vector3(rootPosition);
        initializeJointPositions();
        this.solver = IKSolverFactory.getSolver(definition);
    }

    private void initializeJointPositions() {
        Vector3 pos = new Vector3(rootPosition);
        Vector3 direction;
        if (targetPosition != null && targetPosition.distanceSquared(rootPosition) > 0.01f) {
            direction = targetPosition.subtract(rootPosition).normalize();
        } else {
            direction = new Vector3(1, -0.5f, 0).normalize();
        }
        state.jointPositions.set(0, new Vector3(pos));
        for (int i = 0; i < definition.joints.size(); i++) {
            float length = definition.joints.get(i).length;
            pos = pos.add(direction.multiply(length));
            state.jointPositions.set(i + 1, new Vector3(pos));
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public IKChainDefinition getDefinition() {
        return definition;
    }

    @Override
    public IKChainState getState() {
        return state;
    }

    @Override
    public Vector3 getRootPosition() {
        return rootPosition;
    }

    @Override
    public void setRootPosition(Vector3 position) {
        this.rootPosition = new Vector3(position);
        this.dirty = true;
    }

    public void setStepHeight(float height) {
        this.stepHeight = Math.max(0.0f, height);
    }

    public void setStepParameters(float height, float durationSeconds) {
        this.stepHeight = Math.max(0.0f, height);
        this.stepDurationSeconds = Math.max(0.05f, durationSeconds);
    }

    @Override
    public Vector3 getTarget() {
        return targetPosition;
    }

    @Override
    public void setTarget(Vector3 target) {
        if (target == null) {
            return;
        }
        Vector3 nextTarget = new Vector3(target);
        float deltaSq = this.targetPosition != null ? this.targetPosition.distanceSquared(nextTarget) : Float.MAX_VALUE;
        if (deltaSq > 0.01f) {
            if (!isStepping) {
                this.stepStartPosition = new Vector3(currentSolverTarget);
                this.stepProgress = 0.0f;
                this.isStepping = true;
            }
        }
        this.targetPosition = nextTarget;
        this.dirty = true;
    }

    @Override
    public IKChainState solve() {
        updateRootFromAttachment();
        float speedScale = Math.max(0.01f, interpolationFactor);
        float effectiveDuration = Math.max(0.01f, stepDurationSeconds / speedScale);
        if (isStepping) {
            stepProgress += TICK_DELTA_SECONDS / effectiveDuration;
            if (stepProgress >= 1.0f) {
                stepProgress = 1.0f;
                isStepping = false;
                currentSolverTarget = new Vector3(targetPosition);
            } else {
                Vector3 along = stepStartPosition.lerp(targetPosition, stepProgress);
                float arc = (float) Math.sin(stepProgress * Math.PI) * stepHeight;
                currentSolverTarget = new Vector3(along.x, along.y + arc, along.z);
            }
            dirty = true;
        } else {
        }
        float maxReach = definition.getTotalLength();
        Vector3 toTarget = currentSolverTarget.subtract(rootPosition);
        float dist = toTarget.length();
        if (dist > maxReach && maxReach > 1e-4f) {
            Vector3 clamped = rootPosition.add(toTarget.normalize().multiply(maxReach));
            currentSolverTarget = clamped;
        }
        solver.solve(state, currentSolverTarget, rootPosition, definition);
        state.targetPosition = new Vector3(currentSolverTarget);
        state.targetReached = !isStepping;
        state.timestamp = service.getCurrentTick();
        dirty = isStepping;
        return state;
    }

    @Override
    public IKChainState solveAndBroadcast() {
        IKChainState result = solve();
        service.broadcastChainUpdate(this);
        lastBroadcastTick = service.getCurrentTick();
        return result;
    }

    @Override
    public void setPoleVector(int jointIndex, Vector3 poleVector) {
        if (jointIndex >= 0 && jointIndex < definition.joints.size()) {
            IKChainDefinition.JointDefinition joint = definition.joints.get(jointIndex);
            if (joint.constraints == null) {
                joint.constraints = new IKConstraints();
            }
            joint.constraints.setPoleVector(poleVector);
            dirty = true;
        }
    }

    @Override
    public void setJointConstraints(int jointIndex, IKConstraints constraints) {
        if (jointIndex >= 0 && jointIndex < definition.joints.size()) {
            definition.joints.get(jointIndex).setConstraints(constraints);
            dirty = true;
        }
    }

    @Override
    public void setAutoSolve(boolean enabled) {
        this.autoSolve = enabled;
    }

    @Override
    public boolean isAutoSolveEnabled() {
        return autoSolve;
    }

    @Override
    public void setInterpolationFactor(float factor) {
        this.interpolationFactor = Math.max(0, Math.min(1, factor));
    }

    @Override
    public void attachToModel(long modelId, Vector3 offset) {
        this.attachedModelId = modelId;
        this.attachedEntityUuid = null;
        this.attachOffset = offset != null ? new Vector3(offset) : Vector3.zero();
        service.broadcastAttachment(this);
    }

    @Override
    public void attachToEntity(String entityUuid, Vector3 offset) {
        this.attachedModelId = -1;
        this.attachedEntityUuid = entityUuid;
        this.attachOffset = offset != null ? new Vector3(offset) : Vector3.zero();
        service.broadcastAttachment(this);
    }

    @Override
    public void detach() {
        this.attachedModelId = -1;
        this.attachedEntityUuid = null;
        service.broadcastDetachment(this);
    }

    @Override
    public long getAttachedModelId() {
        return attachedModelId;
    }

    @Override
    public String getAttachedEntityUuid() {
        return attachedEntityUuid;
    }

    @Override
    public boolean isAttached() {
        return attachedModelId >= 0 || attachedEntityUuid != null;
    }

    @Override
    public void remove() {
        if (!removed) {
            removed = true;
            service.removeChainInternal(this);
        }
    }

    public void tick(long currentTick, int broadcastRate) {
        if (removed) return;
        updateRootFromAttachment();
        if (autoSolve && (dirty || isStepping)) {
            solve();
        }
        if ((dirty || isStepping) && (currentTick - lastBroadcastTick) >= broadcastRate) {
            service.broadcastChainUpdate(this);
            lastBroadcastTick = currentTick;
            if (!isStepping) {
                dirty = false;
            }
        }
    }

    private void updateRootFromAttachment() {
        if (attachedModelId >= 0) {
            ModelProxy proxy = ModelManager.getInstance().getById(attachedModelId);
            if (proxy != null) {
                Vector3 modelPos = proxy.getPosition();
                rootPosition = modelPos.add(attachOffset);
            }
        } else if (attachedEntityUuid != null) {
            try {
                UUID uuid = UUID.fromString(attachedEntityUuid);
                Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
                if (player != null) {
                    rootPosition = new Vector3(
                            (float) player.getPosition().x(),
                            (float) player.getPosition().y(),
                            (float) player.getPosition().z()
                    ).add(attachOffset);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    public Vector3 getAttachOffset() {
        return attachOffset;
    }

    public long getLastBroadcastTick() {
        return lastBroadcastTick;
    }
}