package com.moud.server.proxy;

import com.moud.api.ik.IKChainDefinition;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.ik.IKHandle;
import com.moud.server.ik.IKServiceImpl;
import com.moud.server.ts.TsExpose;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@TsExpose
public class IKAPIProxy {
    private final IKServiceImpl service;

    public IKAPIProxy(IKServiceImpl service) {
        this.service = service;
    }

    @HostAccess.Export
    public IKHandleProxy createSpiderLegChain(String id, double coxaLength, double femurLength, double tibiaLength, Object rootPosition) {
        IKHandle handle = service.createSpiderLegChain(id, (float) coxaLength, (float) femurLength, (float) tibiaLength, toVector(rootPosition, Vector3.zero()));
        return wrap(handle);
    }

    /**
     * Creates a spider leg chain with a specific outward direction for proper bending.
     * @param id Chain identifier
     * @param coxaLength Length of hip segment
     * @param femurLength Length of upper leg segment
     * @param tibiaLength Length of lower leg segment
     * @param rootPosition Starting position of the leg
     * @param outwardDirection Direction pointing away from the spider body (determines bend direction)
     */
    @HostAccess.Export
    public IKHandleProxy createSpiderLegChainWithPole(String id, double coxaLength, double femurLength,
                                                       double tibiaLength, Object rootPosition, Object outwardDirection) {
        Vector3 root = toVector(rootPosition, Vector3.zero());
        Vector3 outward = toVector(outwardDirection, Vector3.forward());
        IKHandle handle = service.createSpiderLegChainWithPole(id, (float) coxaLength, (float) femurLength,
                (float) tibiaLength, root, outward);
        return wrap(handle);
    }

    @HostAccess.Export
    public IKHandleProxy createTwoBoneChain(String id, double upperLength, double lowerLength, Object rootPosition, Object poleVector) {
        Vector3 root = toVector(rootPosition, Vector3.zero());
        Vector3 pole = toVector(poleVector, Vector3.forward());
        IKHandle handle = service.createTwoBoneChain(id, (float) upperLength, (float) lowerLength, root, pole);
        return wrap(handle);
    }

    @HostAccess.Export
    public IKHandleProxy createUniformChain(String id, int segmentCount, double segmentLength, Object rootPosition) {
        IKHandle handle = service.createUniformChain(id, segmentCount, (float) segmentLength, toVector(rootPosition, Vector3.zero()));
        return wrap(handle);
    }

    @HostAccess.Export
    public IKHandleProxy createChain(Object definition, Object rootPosition) {
        IKChainDefinition def = toDefinition(definition);
        if (def == null) {
            return null;
        }
        IKHandle handle = service.createChain(def, toVector(rootPosition, Vector3.zero()));
        return wrap(handle);
    }

    @HostAccess.Export
    public IKHandleProxy getChain(String chainId) {
        IKHandle handle = service.getChain(chainId);
        return handle != null ? wrap(handle) : null;
    }

    @HostAccess.Export
    public List<IKHandleProxy> getAllChains() {
        Collection<IKHandle> handles = service.getAllChains();
        return handles.stream().map(this::wrap).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @HostAccess.Export
    public boolean removeChain(String chainId) {
        return service.removeChain(chainId);
    }

    @HostAccess.Export
    public void removeAllChainsForModel(long modelId) {
        service.removeAllChainsForModel(modelId);
    }

    @HostAccess.Export
    public void removeAllChainsForEntity(String entityUuid) {
        service.removeAllChainsForEntity(entityUuid);
    }

    @HostAccess.Export
    public void removeAll() {
        service.getAllChains().forEach(IKHandle::remove);
    }

    @HostAccess.Export
    public void setDefaultBroadcastRate(int ticks) {
        service.setDefaultBroadcastRate(ticks);
    }

    @HostAccess.Export
    public Map<String, Object> raycastGround(Object options) {
        Map<?, ?> map = options instanceof Map<?, ?> m ? m : (options instanceof Value v && v.hasMembers() ? v.as(Map.class) : null);
        if (map == null) {
            return null;
        }
        Object posObj = map.get("position");
        double maxDistance = map.containsKey("maxDistance") && map.get("maxDistance") instanceof Number n ? n.doubleValue() : 256.0;
        Vector3 pos = toVector(posObj, null);
        if (pos == null) {
            return null;
        }
        var instance = com.moud.server.instance.InstanceManager.getInstance().getDefaultInstance();
        var hit = com.moud.server.physics.mesh.ChunkRaycastUtil.raycastDown(instance, pos, maxDistance);
        if (hit == null) {
            return null;
        }
        return java.util.Map.of(
                "position", hit.position(),
                "normal", hit.normal(),
                "distance", hit.distance()
        );
    }

    private IKHandleProxy wrap(IKHandle handle) {
        return handle != null ? new IKHandleProxy(handle) : null;
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

    private IKChainDefinition toDefinition(Object raw) {
        if (raw instanceof IKChainDefinition def) {
            return def;
        }
        if (raw instanceof Value value && value.hasMembers()) {
            return buildDefinition(value.as(Map.class));
        }
        if (raw instanceof Map<?, ?> map) {
            return buildDefinition(map);
        }
        return null;
    }

    private IKChainDefinition buildDefinition(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        String id = map.containsKey("id") && map.get("id") != null ? map.get("id").toString() : null;
        IKChainDefinition def = new IKChainDefinition(id);
        if (map.containsKey("joints")) {
            Object jointsRaw = map.get("joints");
            List<Object> joints = new ArrayList<>();
            if (jointsRaw instanceof List<?>) {
                joints.addAll((List<?>) jointsRaw);
            } else if (jointsRaw instanceof Value val && val.hasArrayElements()) {
                for (long i = 0; i < val.getArraySize(); i++) {
                    joints.add(val.getArrayElement(i).as(Object.class));
                }
            }
            for (Object joint : joints) {
                if (joint instanceof Map<?, ?> jointMap) {
                    String name = jointMap.containsKey("name") && jointMap.get("name") != null ? jointMap.get("name").toString() : null;
                    float length = jointMap.containsKey("length") && jointMap.get("length") instanceof Number n ? n.floatValue() : 0.0f;
                    def.addJoint(name != null ? name : "joint_" + def.joints.size(), length);
                }
            }
        }
        return def;
    }
}
