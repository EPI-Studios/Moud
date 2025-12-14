package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import com.moud.plugin.api.services.primitives.PrimitiveMaterial;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.primitives.PrimitiveServiceImpl;
import com.moud.server.ts.TsExpose;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.stream.Collectors;

@TsExpose
public class PrimitiveAPIProxy {
    private final PrimitiveServiceImpl service;

    public PrimitiveAPIProxy(PrimitiveServiceImpl service) {
        this.service = service;
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createCube(Object position, Object scale, Object material) {
        return wrap(service.createCube(requireVector(position, Vector3.zero()), requireVector(scale, Vector3.one()), buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createSphere(Object position, double radius, Object material) {
        return wrap(service.createSphere(requireVector(position, Vector3.zero()), (float) radius, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createCylinder(Object position, double radius, double height, Object material) {
        return wrap(service.createCylinder(requireVector(position, Vector3.zero()), (float) radius, (float) height, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createCapsule(Object position, double radius, double height, Object material) {
        return wrap(service.createCapsule(requireVector(position, Vector3.zero()), (float) radius, (float) height, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createCone(Object position, double radius, double height, Object material) {
        return wrap(service.createCone(requireVector(position, Vector3.zero()), (float) radius, (float) height, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createPlane(Object position, double width, double depth, Object material) {
        return wrap(service.createPlane(requireVector(position, Vector3.zero()), (float) width, (float) depth, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createLine(Object start, Object end, Object material) {
        return wrap(service.createLine(requireVector(start, Vector3.zero()), requireVector(end, Vector3.zero()), buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createLineStrip(Object points, Object material) {
        List<Vector3> verts = toVectorList(points);
        return wrap(service.createLineStrip(verts, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createBone(Object from, Object to, double thickness, Object material) {
        return wrap(service.createBone(requireVector(from, Vector3.zero()), requireVector(to, Vector3.zero()), (float) thickness, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy createJoint(Object position, double radius, Object material) {
        return wrap(service.createSphere(requireVector(position, Vector3.zero()), (float) radius, buildMaterial(material)));
    }

    @HostAccess.Export
    public PrimitiveHandleProxy create(Object type, Object options) {
        PrimitiveType parsedType = parseType(type);
        if (parsedType == null) {
            return null;
        }
        Vector3 position = Vector3.zero();
        Quaternion rotation = Quaternion.identity();
        Vector3 scale = Vector3.one();
        PrimitiveMaterial material = buildMaterial(options instanceof Map<?, ?> map ? map.get("material") : null);
        String groupId = null;
        Map<?, ?> optsMap = toMap(options);
        if (optsMap != null) {
            if (optsMap.containsKey("position")) {
                position = toVector(optsMap.get("position"), Vector3.zero());
            }
            if (optsMap.containsKey("rotation")) {
                rotation = toQuaternion(optsMap.get("rotation"), Quaternion.identity());
            }
            if (optsMap.containsKey("scale")) {
                scale = toVector(optsMap.get("scale"), Vector3.one());
            }
            if (optsMap.containsKey("material")) {
                material = buildMaterial(optsMap.get("material"));
            }
            if (optsMap.containsKey("groupId")) {
                Object raw = optsMap.get("groupId");
                groupId = raw != null ? raw.toString() : null;
            }
        }
        PrimitiveHandle handle = service.create(parsedType, position, rotation, scale, material, groupId);
        return wrap(handle);
    }

    @HostAccess.Export
    public PrimitiveHandleProxy getPrimitive(double id) {
        PrimitiveHandle handle = service.getPrimitive((long) id);
        return handle != null ? wrap(handle) : null;
    }

    @HostAccess.Export
    public List<PrimitiveHandleProxy> getAllPrimitives() {
        Collection<PrimitiveHandle> all = service.getAllPrimitives();
        return all.stream().map(this::wrap).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @HostAccess.Export
    public List<PrimitiveHandleProxy> getPrimitivesInGroup(String groupId) {
        if (groupId == null) {
            return List.of();
        }
        return service.getPrimitivesInGroup(groupId).stream()
                .map(this::wrap)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @HostAccess.Export
    public boolean removePrimitive(double id) {
        return service.removePrimitive((long) id);
    }

    @HostAccess.Export
    public void removeGroup(String groupId) {
        service.removeGroup(groupId);
    }

    @HostAccess.Export
    public void removeAll() {
        service.removeAll();
    }

    @HostAccess.Export
    public void beginBatch() {
        service.beginBatch();
    }

    @HostAccess.Export
    public void endBatch() {
        service.endBatch();
    }

    @HostAccess.Export
    public boolean isBatching() {
        return service.isBatching();
    }

    private PrimitiveHandleProxy wrap(PrimitiveHandle handle) {
        return handle != null ? new PrimitiveHandleProxy(handle) : null;
    }

    private PrimitiveMaterial buildMaterial(Object raw) {
        PrimitiveMaterial mat = new PrimitiveMaterial();
        if (raw == null) {
            return mat;
        }
        Map<?, ?> map = toMap(raw);
        if (map == null) {
            return mat;
        }
        if (map.containsKey("r")) mat.r = toFloat(map.get("r"), mat.r);
        if (map.containsKey("g")) mat.g = toFloat(map.get("g"), mat.g);
        if (map.containsKey("b")) mat.b = toFloat(map.get("b"), mat.b);
        if (map.containsKey("a")) mat.a = toFloat(map.get("a"), mat.a);
        if (map.containsKey("texture")) {
            Object tex = map.get("texture");
            mat.texture = tex != null ? tex.toString() : null;
        }
        if (map.containsKey("unlit")) mat.unlit = toBoolean(map.get("unlit"), mat.unlit);
        if (map.containsKey("doubleSided")) mat.doubleSided = toBoolean(map.get("doubleSided"), mat.doubleSided);
        if (map.containsKey("renderThroughBlocks"))
            mat.renderThroughBlocks = toBoolean(map.get("renderThroughBlocks"), mat.renderThroughBlocks);
        return mat;
    }

    private PrimitiveType parseType(Object type) {
        if (type instanceof PrimitiveType primitiveType) {
            return primitiveType;
        }
        if (type instanceof Value value && value.isString()) {
            return parseType(value.asString());
        }
        if (type instanceof String str) {
            try {
                return PrimitiveType.valueOf(str.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<?, ?> toMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        if (raw instanceof Value value && value.hasMembers()) {
            return value.as(Map.class);
        }
        return null;
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

    private Quaternion toQuaternion(Object raw, Quaternion fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Quaternion q) {
            return new Quaternion(q);
        }
        if (raw instanceof Map<?, ?> map) {
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            Object wObj = map.get("w");
            if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number && wObj instanceof Number) {
                return new Quaternion(((Number) xObj).floatValue(), ((Number) yObj).floatValue(),
                        ((Number) zObj).floatValue(), ((Number) wObj).floatValue());
            }
        }
        if (raw instanceof Value value && value.hasMembers()) {
            float x = value.hasMember("x") ? value.getMember("x").asFloat() : 0f;
            float y = value.hasMember("y") ? value.getMember("y").asFloat() : 0f;
            float z = value.hasMember("z") ? value.getMember("z").asFloat() : 0f;
            float w = value.hasMember("w") ? value.getMember("w").asFloat() : 1f;
            return new Quaternion(x, y, z, w);
        }
        return fallback;
    }

    private List<Vector3> toVectorList(Object raw) {
        List<Vector3> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object obj : list) {
                Vector3 v = toVector(obj, null);
                if (v != null) {
                    result.add(v);
                }
            }
        } else if (raw instanceof Value value && value.hasArrayElements()) {
            long size = value.getArraySize();
            for (long i = 0; i < size; i++) {
                Vector3 v = toVector(value.getArrayElement(i), null);
                if (v != null) {
                    result.add(v);
                }
            }
        } else {
            Vector3 v = toVector(raw, null);
            if (v != null) {
                result.add(v);
            }
        }
        return result;
    }

    private float toFloat(Object raw, float fallback) {
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private boolean toBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }

    private Vector3 requireVector(Object raw, Vector3 fallback) {
        Vector3 v = toVector(raw, fallback);
        return v != null ? v : fallback;
    }
}