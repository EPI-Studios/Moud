package com.moud.server.proxy;

import com.moud.api.ik.IKChainDefinition;
import com.moud.api.ik.IKChainState;
import com.moud.api.ik.IKConstraints;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.ik.IKHandle;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Map;

public class IKHandleProxy implements AutoCloseable {
    private final IKHandle handle;

    public IKHandleProxy(IKHandle handle) {
        this.handle = handle;
    }

    @HostAccess.Export
    public String getId() {
        return handle.getId();
    }

    @HostAccess.Export
    public IKChainDefinition getDefinition() {
        return handle.getDefinition();
    }

    @HostAccess.Export
    public IKChainState getState() {
        return handle.getState();
    }

    @HostAccess.Export
    public void setRootPosition(Object position) {
        Vector3 pos = toVector(position, handle.getRootPosition());
        if (pos != null) {
            handle.setRootPosition(pos);
        }
    }

    @HostAccess.Export
    public Vector3 getRootPosition() {
        return handle.getRootPosition();
    }

    @HostAccess.Export
    public void setTarget(Object target) {
        Vector3 tgt = toVector(target, handle.getTarget());
        if (tgt != null) {
            handle.setTarget(tgt);
        }
    }

    @HostAccess.Export
    public Vector3 getTarget() {
        return handle.getTarget();
    }

    @HostAccess.Export
    public IKChainState solve() {
        return handle.solve();
    }

    @HostAccess.Export
    public IKChainState solveAndBroadcast() {
        return handle.solveAndBroadcast();
    }

    @HostAccess.Export
    public void setPoleVector(int jointIndex, Object poleVector) {
        Vector3 vec = toVector(poleVector, null);
        if (vec != null) {
            handle.setPoleVector(jointIndex, vec);
        }
    }

    @HostAccess.Export
    public void setJointConstraints(int jointIndex, IKConstraints constraints) {
        if (constraints != null) {
            handle.setJointConstraints(jointIndex, constraints);
        }
    }

    @HostAccess.Export
    public void setAutoSolve(boolean enabled) {
        handle.setAutoSolve(enabled);
    }

    @HostAccess.Export
    public boolean isAutoSolveEnabled() {
        return handle.isAutoSolveEnabled();
    }

    @HostAccess.Export
    public void setInterpolationFactor(double factor) {
        handle.setInterpolationFactor((float) factor);
    }

    @HostAccess.Export
    public void attachToModel(long modelId, Object offset) {
        handle.attachToModel(modelId, toVector(offset, Vector3.zero()));
    }

    @HostAccess.Export
    public void attachToEntity(String entityUuid, Object offset) {
        handle.attachToEntity(entityUuid, toVector(offset, Vector3.zero()));
    }

    @HostAccess.Export
    public void detach() {
        handle.detach();
    }

    @HostAccess.Export
    public long getAttachedModelId() {
        return handle.getAttachedModelId();
    }

    @HostAccess.Export
    public String getAttachedEntityUuid() {
        return handle.getAttachedEntityUuid();
    }

    @HostAccess.Export
    public boolean isAttached() {
        return handle.isAttached();
    }

    @HostAccess.Export
    public void remove() {
        handle.remove();
    }

    @Override
    public void close() {
        remove();
    }

    private Vector3 toVector(Object raw, Vector3 fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Vector3 v) {
            return new Vector3(v);
        }
        if (raw instanceof Map<?, ?> map) {
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number) {
                return new Vector3(((Number) xObj).floatValue(), ((Number) yObj).floatValue(), ((Number) zObj).floatValue());
            }
        }
        if (raw instanceof Value value && value.hasMembers()) {
            float x = value.hasMember("x") ? value.getMember("x").asFloat() : 0f;
            float y = value.hasMember("y") ? value.getMember("y").asFloat() : 0f;
            float z = value.hasMember("z") ? value.getMember("z").asFloat() : 0f;
            return new Vector3(x, y, z);
        }
        return fallback;
    }
}
