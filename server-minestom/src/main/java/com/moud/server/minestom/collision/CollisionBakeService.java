package com.moud.server.minestom.collision;

import com.moud.core.physics.CollisionGeometry;
import com.moud.core.scene.Node;
import com.moud.server.minestom.assets.AssetStore;
import com.moud.server.minestom.collision.source.BbmodelGeometrySource;
import com.moud.server.minestom.collision.source.BuiltinMeshGeometrySource;
import com.moud.server.minestom.collision.source.GeometrySourceRegistry;
import com.moud.server.minestom.collision.source.ProceduralMeshGeometrySource;
import com.moud.server.minestom.engine.Engine;
import com.moud.server.minestom.mesh.ServerArrayMeshResolver;
import com.moud.server.minestom.project.ProjectService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class CollisionBakeService {
    public record BakeResult(List<CollisionGeometry> hulls, CollisionStrategy resolved) {
        public BakeResult {
            hulls = hulls == null ? List.of() : List.copyOf(hulls);
        }
    }

    private final GeometrySourceRegistry geometrySources;
    private final ServerArrayMeshResolver meshResolver;
    private BiConsumer<Long, BakeResult> bakeListener;

    public CollisionBakeService(ProjectService project, AssetStore assets) {
        this.meshResolver = new ServerArrayMeshResolver(assets);
        this.geometrySources = new GeometrySourceRegistry(List.of(
                new ProceduralMeshGeometrySource(meshResolver),
                new BuiltinMeshGeometrySource(),
                new BbmodelGeometrySource(project, assets)
        ));
    }

    public ServerArrayMeshResolver meshResolver() {
        return meshResolver;
    }

    public void setBakeListener(BiConsumer<Long, BakeResult> listener) {
        this.bakeListener = listener;
    }

    public void invalidateNode(long nodeId) {
    }

    public boolean supportsGeometryNode(String typeId) {
        return geometrySources.supports(typeId);
    }

    public BakeResult bake(Node node, String typeId, Engine engine, CollisionUsage usage) {
        if (node == null || typeId == null) {
            return new BakeResult(List.of(), CollisionStrategy.AUTO);
        }
        CollisionStrategy requested = strategyForNode(node, typeId, usage);
        if (isPrimitiveStrategy(requested)) {
            return new BakeResult(List.of(), requested);
        }
        CollisionGeometry geometry = extractGeometry(node, typeId, engine);
        if (geometry.isEmpty()) {
            return new BakeResult(List.of(), CollisionStrategy.BOX);
        }
        CollisionStrategy resolved = requested == CollisionStrategy.AUTO
                ? (usage == CollisionUsage.STATIC ? CollisionStrategy.MESH : CollisionStrategy.BOX)
                : requested;
        if (resolved == CollisionStrategy.VHACD || resolved == CollisionStrategy.CONVEX) {
            resolved = usage == CollisionUsage.STATIC ? CollisionStrategy.MESH : CollisionStrategy.BOX;
        }
        BakeResult result = new BakeResult(List.of(geometry), resolved);
        notifyBaked(node.nodeId(), result);
        return result;
    }

    public void notifyBaked(long nodeId, BakeResult result) {
        BiConsumer<Long, BakeResult> listener = bakeListener;
        if (listener != null && result != null && !result.hulls().isEmpty()) {
            listener.accept(nodeId, result);
        }
    }

    public CollisionGeometry extractGeometry(Node node, String typeId, Engine engine) {
        if (node == null || typeId == null) {
            return CollisionGeometry.EMPTY;
        }
        if ("RigidBody3D".equals(typeId) || "StaticBody3D".equals(typeId) || "Area3D".equals(typeId)) {
            return extractBodyGeometry(node, engine);
        }
        return extractSelfGeometry(node, typeId);
    }

    private CollisionGeometry extractBodyGeometry(Node bodyNode, Engine engine) {
        if (bodyNode == null || engine == null) {
            return CollisionGeometry.EMPTY;
        }
        GeometryAccumulator out = new GeometryAccumulator();
        ArrayDeque<Item> stack = new ArrayDeque<>();
        for (Node child : bodyNode.children()) {
            if (child == null) {
                continue;
            }
            String typeId = engine.nodeTypes().typeIdFor(child);
            SceneNodeTransform.Transform local = SceneNodeTransform.localTransform(child, typeId);
            SceneNodeTransform.Transform world = SceneNodeTransform.shouldInheritTransform(child)
                    ? SceneNodeTransform.Transform.IDENTITY.compose(local)
                    : local;
            stack.push(new Item(child, typeId, world));
        }

        while (!stack.isEmpty()) {
            Item item = stack.pop();
            if (item.node == null || item.typeId == null) {
                continue;
            }
            if (isPhysicsNode(item.typeId)) {
                continue;
            }
            if (geometrySources.supports(item.typeId)) {
                out.append(geometrySources.extract(item.node, item.typeId), item.transform);
            }
            for (Node child : item.node.children()) {
                if (child == null) {
                    continue;
                }
                String childType = engine.nodeTypes().typeIdFor(child);
                SceneNodeTransform.Transform local = SceneNodeTransform.localTransform(child, childType);
                SceneNodeTransform.Transform world = SceneNodeTransform.shouldInheritTransform(child)
                        ? item.transform.compose(local)
                        : local;
                stack.push(new Item(child, childType, world));
            }
        }
        return out.build();
    }

    private CollisionGeometry extractSelfGeometry(Node node, String typeId) {
        return geometrySources.extract(node, typeId);
    }

    public static CollisionStrategy strategyForNode(Node node, String typeId, CollisionUsage usage) {
        if ("RigidBody3D".equals(typeId) || "StaticBody3D".equals(typeId) || "Area3D".equals(typeId)) {
            return CollisionStrategy.parse(node.getProperty("shape"), CollisionStrategy.AUTO);
        }
        return CollisionStrategy.parse(node.getProperty("collision_strategy"),
                usage == CollisionUsage.STATIC ? CollisionStrategy.AUTO : CollisionStrategy.BOX);
    }

    public static boolean isPrimitiveStrategy(CollisionStrategy strategy) {
        return strategy == CollisionStrategy.BOX
                || strategy == CollisionStrategy.SPHERE
                || strategy == CollisionStrategy.CAPSULE;
    }

    private static boolean isPhysicsNode(String typeId) {
        return "RigidBody3D".equals(typeId)
                || "StaticBody3D".equals(typeId)
                || "CharacterBody3D".equals(typeId)
                || "Area3D".equals(typeId)
                || "Raycast3D".equals(typeId);
    }

    private record Item(Node node, String typeId, SceneNodeTransform.Transform transform) {
    }

    private static final class GeometryAccumulator {
        private final ArrayList<Float> vertices = new ArrayList<>();
        private final ArrayList<Integer> indices = new ArrayList<>();

        void append(CollisionGeometry geometry, SceneNodeTransform.Transform transform) {
            if (geometry == null || geometry.isEmpty()) {
                return;
            }
            int base = vertices.size() / 3;
            float[] sourceVertices = geometry.vertices();
            for (int i = 0; i < sourceVertices.length; i += 3) {
                SceneNodeTransform.Vec3 v = transform.apply(sourceVertices[i], sourceVertices[i + 1], sourceVertices[i + 2]);
                vertices.add((float) v.x());
                vertices.add((float) v.y());
                vertices.add((float) v.z());
            }
            int[] sourceIndices = geometry.indices();
            for (int index : sourceIndices) {
                indices.add(base + index);
            }
        }

        CollisionGeometry build() {
            if (vertices.isEmpty() || indices.isEmpty()) {
                return CollisionGeometry.EMPTY;
            }
            float[] vertexArray = new float[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) {
                vertexArray[i] = vertices.get(i);
            }
            int[] indexArray = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                indexArray[i] = indices.get(i);
            }
            return new CollisionGeometry(vertexArray, indexArray);
        }
    }
}
