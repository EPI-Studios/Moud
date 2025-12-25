package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrimitiveHandleProxy implements AutoCloseable {
    private final PrimitiveHandle handle;

    public PrimitiveHandleProxy(PrimitiveHandle handle) {
        this.handle = handle;
    }

    @HostAccess.Export
    public long getId() {
        return handle.getId();
    }

    @HostAccess.Export
    public String getType() {
        return handle.getType() != null ? handle.getType().name().toLowerCase() : "";
    }

    @HostAccess.Export
    public String getGroupId() {
        return handle.getGroupId();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return handle.getPosition();
    }

    @HostAccess.Export
    public void setPosition(Object position) {
        Vector3 pos = toVector(position, null);
        if (pos != null) {
            handle.setPosition(pos);
        }
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        return handle.getRotation();
    }

    @HostAccess.Export
    public void setRotation(Object rotation) {
        Quaternion rot = toQuaternion(rotation, null);
        if (rot != null) {
            handle.setRotation(rot);
        }
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return handle.getScale();
    }

    @HostAccess.Export
    public void setScale(Object scale) {
        Vector3 scl = toVector(scale, null);
        if (scl != null) {
            handle.setScale(scl);
        }
    }

    @HostAccess.Export
    public void setTransform(Object position, Object rotation, Object scale) {
        handle.setTransform(toVector(position, handle.getPosition()), toQuaternion(rotation, handle.getRotation()), toVector(scale, handle.getScale()));
    }

    @HostAccess.Export
    public void setFromTo(Object from, Object to, double thickness) {
        Vector3 a = toVector(from, null);
        Vector3 b = toVector(to, null);
        if (a == null || b == null) {
            return;
        }
        handle.setFromTo(a, b, (float) thickness);
    }

    @HostAccess.Export
    public void setColor(double r, double g, double b) {
        handle.setColor((float) r, (float) g, (float) b);
    }

    @HostAccess.Export
    public void setColorAlpha(double r, double g, double b, double a) {
        handle.setColor((float) r, (float) g, (float) b, (float) a);
    }

    @HostAccess.Export
    public void setUnlit(boolean unlit) {
        handle.setUnlit(unlit);
    }

    @HostAccess.Export
    public void setRenderThroughBlocks(boolean enabled) {
        handle.setRenderThroughBlocks(enabled);
    }

    @HostAccess.Export
    public void setDoubleSided(boolean doubleSided) {
        handle.setDoubleSided(doubleSided);
    }

    @HostAccess.Export
    public void setTexture(String texturePath) {
        handle.setTexture(texturePath);
    }

    @HostAccess.Export
    public void setVertices(Object value) {
        List<Vector3> verts = toVectorList(value);
        if (verts != null) {
            handle.setVertices(verts);
        }
    }

    @HostAccess.Export
    public void setMesh(Object vertices, Object indices) {
        List<Vector3> verts = toVectorList(vertices);
        List<Integer> inds = toIntList(indices);
        if (verts == null && inds == null) {
            return;
        }
        handle.setMesh(verts, inds);
    }

    @HostAccess.Export
    public void remove() {
        handle.remove();
    }

    @HostAccess.Export
    public boolean isRemoved() {
        return handle.isRemoved();
    }

    @Override
    public void close() {
        remove();
    }

    private List<Vector3> toVectorList(Object value) {
        if (value == null) {
            return null;
        }
        List<Vector3> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object obj : list) {
                Vector3 v = toVector(obj);
                if (v != null) {
                    result.add(v);
                }
            }
            return result;
        }
        if (value instanceof Value val && val.hasArrayElements()) {
            long size = val.getArraySize();
            for (long i = 0; i < size; i++) {
                Vector3 v = toVector(val.getArrayElement(i));
                if (v != null) {
                    result.add(v);
                }
            }
            return result;
        }
        Vector3 single = toVector(value);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    private List<Integer> toIntList(Object raw) {
        if (raw == null) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        if (raw instanceof Number number) {
            result.add(number.intValue());
            return result;
        }
        if (raw instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Number n) {
                    result.add(n.intValue());
                }
            }
            return result;
        }
        if (raw instanceof Value value) {
            if (value.isNumber()) {
                result.add(value.asInt());
                return result;
            }
            if (value.hasArrayElements()) {
                long size = value.getArraySize();
                for (long i = 0; i < size; i++) {
                    Value elem = value.getArrayElement(i);
                    if (elem != null && elem.isNumber()) {
                        result.add(elem.asInt());
                    }
                }
                return result;
            }
        }
        return result;
    }

    private Vector3 toVector(Object raw) {
        return toVector(raw, null);
    }

    private Quaternion toQuaternion(Object raw, Quaternion fallback) {
        if (raw == null) {
            return fallback;
        }
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
        if (raw instanceof Value val && val.hasMembers()) {
            float x = val.hasMember("x") ? val.getMember("x").asFloat() : 0f;
            float y = val.hasMember("y") ? val.getMember("y").asFloat() : 0f;
            float z = val.hasMember("z") ? val.getMember("z").asFloat() : 0f;
            float w = val.hasMember("w") ? val.getMember("w").asFloat() : 1f;
            return new Quaternion(x, y, z, w);
        }
        return fallback;
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
        if (raw instanceof Value val && val.hasMembers()) {
            float x = val.hasMember("x") ? val.getMember("x").asFloat() : 0f;
            float y = val.hasMember("y") ? val.getMember("y").asFloat() : 0f;
            float z = val.hasMember("z") ? val.getMember("z").asFloat() : 0f;
            return new Vector3(x, y, z);
        }
        return fallback;
    }
}
