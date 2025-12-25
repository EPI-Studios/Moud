package com.moud.server.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import com.moud.plugin.api.services.primitives.PrimitiveMaterial;
import com.moud.plugin.api.services.primitives.PrimitiveType;
import com.moud.server.primitives.PrimitiveServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public final class PrimitiveRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimitiveRuntimeAdapter.class);

    private final String sceneId;
    private PrimitiveHandle primitive;
    private String objectId;
    private PrimitiveType lastType;
    private boolean lastDynamic;
    private float lastMass;

    public PrimitiveRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        this.objectId = snapshot.objectId();
        recreate(snapshot);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();
        PrimitiveType desiredType = parsePrimitiveType(firstNonNull(
                props.get("primitiveType"),
                props.get("shape"),
                props.get("type")
        ), PrimitiveType.CUBE);
        PhysicsConfig physics = parsePhysics(props.get("physics"));

        if (primitive == null || primitive.isRemoved()) {
            recreate(snapshot);
            return;
        }

        if (desiredType != lastType || physics.dynamic != lastDynamic || Float.compare(physics.mass, lastMass) != 0) {
            recreate(snapshot);
            return;
        }

        Vector3 position = vectorProperty(props.get("position"), primitive.getPosition());
        Quaternion rotation = quaternionProperty(props, primitive.getRotation());
        Vector3 scale = vectorProperty(props.get("scale"), primitive.getScale());
        primitive.setTransform(position, rotation, scale);

        PrimitiveMaterial desiredMaterial = parseMaterialFromProps(props);
        applyMaterial(desiredMaterial);
    }

    @Override
    public void remove() {
        if (primitive != null) {
            try {
                primitive.remove();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove primitive bound to scene {}", sceneId, e);
            }
            primitive = null;
        }
    }

    private void recreate(MoudPackets.SceneObjectSnapshot snapshot) {
        if (primitive != null && !primitive.isRemoved()) {
            primitive.remove();
        }

        Map<String, Object> props = snapshot.properties();
        PrimitiveType type = parsePrimitiveType(firstNonNull(
                props.get("primitiveType"),
                props.get("shape"),
                props.get("type")
        ), PrimitiveType.CUBE);
        Vector3 position = vectorProperty(props.get("position"), new Vector3(0, 64, 0));
        Quaternion rotation = quaternionProperty(props, Quaternion.identity());
        Vector3 scale = vectorProperty(props.get("scale"), Vector3.one());
        PrimitiveMaterial material = parseMaterialFromProps(props);
        PhysicsConfig physics = parsePhysics(props.get("physics"));

        String groupId = "scene:" + sceneId + ":" + snapshot.objectId();

        PrimitiveServiceImpl service = PrimitiveServiceImpl.getInstance();
        if (physics.dynamic) {
            primitive = service.createWithPhysics(
                    type,
                    position,
                    rotation,
                    scale,
                    material,
                    groupId,
                    true,
                    physics.mass
            );
        } else {
            primitive = service.create(type, position, rotation, scale, material, groupId);
        }

        lastType = type;
        lastDynamic = physics.dynamic;
        lastMass = physics.mass;
    }

    private void applyMaterial(PrimitiveMaterial desired) {
        if (primitive == null || desired == null) {
            return;
        }

        PrimitiveMaterial current = desired;

        primitive.setColor(current.r, current.g, current.b, current.a);
        primitive.setUnlit(current.unlit);
        primitive.setDoubleSided(current.doubleSided);
        primitive.setRenderThroughBlocks(current.renderThroughBlocks);
        primitive.setTexture(current.texture);
    }

    private static PrimitiveType parsePrimitiveType(Object raw, PrimitiveType fallback) {
        if (raw == null) {
            return fallback;
        }
        String token = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return switch (token) {
            case "cube" -> PrimitiveType.CUBE;
            case "sphere" -> PrimitiveType.SPHERE;
            case "cylinder" -> PrimitiveType.CYLINDER;
            case "capsule" -> PrimitiveType.CAPSULE;
            case "line" -> PrimitiveType.LINE;
            case "linestrip", "line_strip", "line-strip" -> PrimitiveType.LINE_STRIP;
            case "plane" -> PrimitiveType.PLANE;
            case "cone" -> PrimitiveType.CONE;
            case "mesh" -> PrimitiveType.MESH;
            default -> fallback;
        };
    }

    @SuppressWarnings("unchecked")
    private static PrimitiveMaterial parseMaterial(Object raw, PrimitiveMaterial fallback) {
        PrimitiveMaterial resolvedFallback = fallback != null ? fallback : PrimitiveMaterial.white();
        if (!(raw instanceof Map<?, ?> map)) {
            return fallback;
        }
        Map<String, Object> materialMap = (Map<String, Object>) map;
        PrimitiveMaterial material = new PrimitiveMaterial();
        material.r = (float) toDouble(materialMap.get("r"), resolvedFallback.r);
        material.g = (float) toDouble(materialMap.get("g"), resolvedFallback.g);
        material.b = (float) toDouble(materialMap.get("b"), resolvedFallback.b);
        material.a = (float) toDouble(materialMap.get("a"), resolvedFallback.a);
        String texture = stringProperty(materialMap, "texture", resolvedFallback.texture);
        material.texture = (texture == null || texture.isBlank()) ? null : texture;
        material.unlit = booleanProperty(materialMap.get("unlit"), resolvedFallback.unlit);
        material.doubleSided = booleanProperty(materialMap.get("doubleSided"), resolvedFallback.doubleSided);
        material.renderThroughBlocks = booleanProperty(materialMap.get("renderThroughBlocks"), resolvedFallback.renderThroughBlocks);
        return material;
    }

    private static PrimitiveMaterial parseMaterialFromProps(Map<String, Object> props) {
        PrimitiveMaterial fallback = PrimitiveMaterial.white();
        PrimitiveMaterial nested = parseMaterial(props.get("material"), null);
        if (nested != null) {
            return nested;
        }

        PrimitiveMaterial material = new PrimitiveMaterial();
        material.r = (float) toDouble(props.get("r"), fallback.r);
        material.g = (float) toDouble(props.get("g"), fallback.g);
        material.b = (float) toDouble(props.get("b"), fallback.b);
        material.a = (float) toDouble(props.get("a"), fallback.a);
        String texture = props.get("texture") != null ? String.valueOf(props.get("texture")) : fallback.texture;
        material.texture = (texture == null || texture.isBlank()) ? null : texture;
        material.unlit = booleanProperty(props.get("unlit"), fallback.unlit);
        material.doubleSided = booleanProperty(props.get("doubleSided"), fallback.doubleSided);
        material.renderThroughBlocks = booleanProperty(props.get("renderThroughBlocks"), fallback.renderThroughBlocks);
        return material;
    }

    private static Vector3 vectorProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback.x);
            double y = toDouble(map.get("y"), fallback.y);
            double z = toDouble(map.get("z"), fallback.z);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private static Quaternion quaternionProperty(Map<String, Object> props, Quaternion fallback) {
        Object rawQuat = props.get("rotationQuat");
        if (rawQuat instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), 0);
            double y = toDouble(map.get("y"), 0);
            double z = toDouble(map.get("z"), 0);
            double w = toDouble(map.get("w"), 1);
            return new Quaternion((float) x, (float) y, (float) z, (float) w);
        }
        return quaternionFromEuler(props.get("rotation"), fallback);
    }

    private static Quaternion quaternionFromEuler(Object raw, Quaternion fallback) {
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            double pitch = hasEuler ? toDouble(map.get("pitch"), 0) : toDouble(map.get("x"), 0);
            double yaw = hasEuler ? toDouble(map.get("yaw"), 0) : toDouble(map.get("y"), 0);
            double roll = hasEuler ? toDouble(map.get("roll"), 0) : toDouble(map.get("z"), 0);
            return Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
        }
        return fallback;
    }

    private static String stringProperty(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static boolean booleanProperty(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw != null) {
            String s = raw.toString().trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("1")) {
                return true;
            }
            if (s.equals("false") || s.equals("no") || s.equals("0")) {
                return false;
            }
        }
        return fallback;
    }

    private static double toDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static PhysicsConfig parsePhysics(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new PhysicsConfig(false, 1.0f);
        }
        boolean dynamic = booleanProperty(((Map<?, ?>) map).get("dynamic"), false);
        float mass = (float) toDouble(((Map<?, ?>) map).get("mass"), 1.0);
        if (!Float.isFinite(mass) || mass <= 0.0f) {
            mass = 1.0f;
        }
        return new PhysicsConfig(dynamic, mass);
    }

    private record PhysicsConfig(boolean dynamic, float mass) {
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
