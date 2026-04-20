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
                PostProcessService.INSTANCE.registerShader(id, source, priority);
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
            String s = entry.trim();
            if (s.isEmpty()) continue;
            int eq = s.indexOf('=');
            if (eq <= 0 || eq == s.length() - 1) continue;
            String key = s.substring(0, eq).trim();
            if (key.isEmpty()) continue;
            String[] parts = s.substring(eq + 1).trim().split(",");
            float[] vals = new float[parts.length];
            boolean ok = true;
            for (int i = 0; i < parts.length; i++) {
                try {
                    vals[i] = Float.parseFloat(parts[i].trim());
                } catch (NumberFormatException e) {
                    ok = false;
                    break;
                }
            }
            if (!ok || vals.length == 0 || vals.length > 4) continue;
            PostProcessService.INSTANCE.setUniform(effectId, key, vals);
        }
    }
}
