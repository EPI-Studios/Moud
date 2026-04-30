package com.moud.client.fabric.scene.interp;

import com.moud.core.interp.InterpDefaults;
import com.moud.core.interp.InterpPolicy;
import com.moud.core.interp.InterpProperty;
import com.moud.net.protocol.SceneSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;

public final class NodeInterpolatorRegistry {

    private static final NodeInterpolatorRegistry INSTANCE = new NodeInterpolatorRegistry();

    private final Long2ObjectOpenHashMap<NodeInterpolator> nodes = new Long2ObjectOpenHashMap<>();
    private final Object lock = new Object();

    private NodeInterpolatorRegistry() {
    }

    public static NodeInterpolatorRegistry get() {
        return INSTANCE;
    }

    public void onNodeUpdated(SceneSnapshot.NodeSnapshot node, long nowNanos) {
        if (node == null || node.nodeId() <= 0L) {
            return;
        }
        InterpPolicy policy = readPolicy(node);

        synchronized (lock) {
            NodeInterpolator interp = nodes.get(node.nodeId());
            if (interp == null) {
                interp = new NodeInterpolator(policy);
                nodes.put(node.nodeId(), interp);
            } else {
                interp.setPolicy(policy);
            }
            ingestProperties(interp, node, nowNanos);
        }
    }

    public void onNodeRemoved(long nodeId) {
        synchronized (lock) {
            nodes.remove(nodeId);
        }
    }

    public boolean fillSampledTransform(long nodeId, SampledTransform out, long sampleNanos) {
        NodeInterpolator interp;
        synchronized (lock) {
            interp = nodes.get(nodeId);
        }
        if (interp == null) {
            return false;
        }
        interp.fill(out, sampleNanos);
        return true;
    }

    public InterpPolicy policyFor(long nodeId) {
        synchronized (lock) {
            NodeInterpolator interp = nodes.get(nodeId);
            return interp == null ? InterpDefaults.current() : interp.policy();
        }
    }

    public void pushTweenedSample(long nodeId, InterpProperty property, float value, long nowNanos) {
        synchronized (lock) {
            NodeInterpolator interp = nodes.get(nodeId);
            if (interp == null) {
                interp = new NodeInterpolator(InterpDefaults.current());
                nodes.put(nodeId, interp);
            }
            interp.push(property, value, nowNanos);
        }
    }

    public void clear() {
        synchronized (lock) {
            nodes.clear();
        }
    }

    private static InterpPolicy readPolicy(SceneSnapshot.NodeSnapshot node) {
        String mode = null;
        String lag = null;
        List<SceneSnapshot.Property> props = node.properties();
        if (props != null) {
            for (SceneSnapshot.Property prop : props) {
                if (prop == null || prop.key() == null) {
                    continue;
                }
                if (InterpDefaults.PROP_INTERP_MODE.equals(prop.key())) {
                    mode = prop.value();
                } else if (InterpDefaults.PROP_INTERP_LAG_MS.equals(prop.key())) {
                    lag = prop.value();
                }
            }
        }
        return InterpDefaults.resolve(mode, lag);
    }

    private static void ingestProperties(NodeInterpolator interp, SceneSnapshot.NodeSnapshot node, long nowNanos) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null || props.isEmpty()) {
            return;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop == null || prop.key() == null) {
                continue;
            }
            InterpProperty mapped = InterpProperty.fromKey(prop.key());
            if (mapped == null) {
                continue;
            }
            float parsed = parseFloat(prop.value());
            if (Float.isNaN(parsed)) {
                continue;
            }
            interp.push(mapped, parsed, nowNanos);
        }
    }

    private static float parseFloat(String value) {
        if (value == null || value.isBlank()) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
