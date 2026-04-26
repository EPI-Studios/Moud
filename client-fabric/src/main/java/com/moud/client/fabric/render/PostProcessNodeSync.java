package com.moud.client.fabric.render;

import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PostProcessNodeSync {

    private final Set<String> activeIds = new HashSet<>();

    void sync(List<SceneSnapshot.NodeSnapshot> nodes) {
        Set<String> wanted = new HashSet<>();
        if (nodes != null) {
            for (SceneSnapshot.NodeSnapshot node : nodes) {
                if (node == null || !"PostProcess".equals(node.type())) continue;
                if (!NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "enabled"), true)) continue;
                String source = NodePropertyUtils.stringProp(node, "source");
                if (source == null || source.isBlank()) continue;

                String id = "node:" + node.nodeId();
                int priority = (int) NodePropertyUtils.parseFloat(NodePropertyUtils.stringProp(node, "priority"), 0f);
                PostProcessStage stage = PostProcessStage.parse(
                        NodePropertyUtils.stringProp(node, "stage"),
                        PostProcessStage.WORLD);
                PostProcessService.INSTANCE.registerShader(id, source, priority, stage);
                pushUniforms(id, NodePropertyUtils.stringProp(node, "uniforms"));
                wanted.add(id);
            }
        }

        for (String stale : new HashSet<>(activeIds)) {
            if (!wanted.contains(stale)) {
                PostProcessService.INSTANCE.unregister(stale);
                activeIds.remove(stale);
            }
        }
        activeIds.addAll(wanted);
    }

    private static void pushUniforms(String effectId, String spec) {
        if (spec == null || spec.isBlank()) return;
        for (String entry : spec.split(";")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String key = entry.substring(0, eq).trim();
            if (key.isEmpty()) continue;
            float[] vals = parseFloats(entry.substring(eq + 1));
            if (vals == null) continue;
            PostProcessService.INSTANCE.setUniform(effectId, key, vals);
        }
    }

    private static float[] parseFloats(String csv) {
        String[] parts = csv.trim().split(",");
        if (parts.length == 0 || parts.length > 4) return null;
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
