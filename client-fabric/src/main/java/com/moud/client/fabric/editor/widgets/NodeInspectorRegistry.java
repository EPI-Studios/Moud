package com.moud.client.fabric.editor.widgets;

import com.moud.client.fabric.editor.widgets.inspectors.Camera3DInspector;
import com.moud.client.fabric.editor.widgets.inspectors.CollisionMaskInspector;
import com.moud.client.fabric.editor.widgets.inspectors.LightColorInspector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class NodeInspectorRegistry {
    private final Map<String, List<Supplier<NodeInspectorWidget>>> factories = new HashMap<>();
    private final Map<String, List<NodeInspectorWidget>> instances = new HashMap<>();

    public NodeInspectorRegistry() {
        register("Camera3D", Camera3DInspector::new);

        register("OmniLight3D", LightColorInspector::new);
        register("SpotLight3D", LightColorInspector::new);
        register("DirectionalLight3D", LightColorInspector::new);

        register("StaticBody3D", CollisionMaskInspector::new);
        register("RigidBody3D", CollisionMaskInspector::new);
        register("CharacterBody3D", CollisionMaskInspector::new);
        register("Area3D", CollisionMaskInspector::new);
        register("Raycast3D", CollisionMaskInspector::new);
    }

    public void register(String nodeType, Supplier<NodeInspectorWidget> factory) {
        factories.computeIfAbsent(nodeType, t -> new ArrayList<>()).add(factory);
    }

    public NodeInspectorWidget findFor(String nodeType, String propertyKey) {
        if (nodeType == null) return null;
        List<NodeInspectorWidget> list = instances.computeIfAbsent(nodeType, t -> {
            List<Supplier<NodeInspectorWidget>> fs = factories.get(t);
            if (fs == null) return List.of();
            List<NodeInspectorWidget> built = new ArrayList<>(fs.size());
            for (Supplier<NodeInspectorWidget> f : fs) built.add(f.get());
            return built;
        });
        for (NodeInspectorWidget w : list) {
            if (w.handles(propertyKey)) return w;
        }
        return null;
    }
}
