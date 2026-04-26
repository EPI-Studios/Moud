package com.moud.server.minestom.collision;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.CapsuleShapeSettings;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Float3;
import com.github.stephengold.joltjni.IndexedTriangle;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.vhacd.ConvexHull;
import com.github.stephengold.joltjni.vhacd.Decomposer;
import com.github.stephengold.joltjni.vhacd.Parameters;
import com.moud.core.scene.Node;
import com.moud.core.physics.CollisionGeometry;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public final class CollisionBakeService {
    private static final float MIN_CAPSULE_HALF_HEIGHT = 1.0e-6f;

    public record BakeResult(ShapeSettings settings, List<CollisionGeometry> hulls) {}

    private final GeometrySourceRegistry geometrySources;
    private BiConsumer<Long, BakeResult> bakeListener;

    private final ServerArrayMeshResolver meshResolver;

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

    public ShapeSettings createShapeSettings(Node node, String typeId, Engine engine, CollisionUsage usage) {
        BakeResult result = bake(node, typeId, engine, usage);
        if (result != null && bakeListener != null) {
            bakeListener.accept(node.nodeId(), result);
        }
        return result != null ? result.settings() : null;
    }

    public BakeResult bake(Node node, String typeId, Engine engine, CollisionUsage usage) {
        if (node == null || typeId == null) {
            return null;
        }

        CollisionStrategy strategy = strategyForNode(node, typeId, usage);
        if (isPrimitiveStrategy(strategy)) {
            return new BakeResult(primitiveSettings(node, strategy), List.of());
        }

        CollisionGeometry geometry = "RigidBody3D".equals(typeId) || "StaticBody3D".equals(typeId)
                ? extractBodyGeometry(node, engine)
                : extractSelfGeometry(node, typeId);
        if (geometry.isEmpty()) {
            if ("RigidBody3D".equals(typeId) || "StaticBody3D".equals(typeId)) {
                return new BakeResult(primitiveSettings(node, CollisionStrategy.BOX), List.of());
            }
            return null;
        }

        CollisionStrategy resolved = resolveAutoStrategy(strategy, usage, geometry);
        return switch (resolved) {
            case CONVEX -> new BakeResult(convexSettings(geometry), List.of(geometry));
            case MESH -> {
                ShapeSettings settings = usage == CollisionUsage.STATIC ? meshSettings(geometry) : convexSettings(geometry);
                yield new BakeResult(settings, List.of(geometry));
            }
            case VHACD -> vhacdResult(geometry, usage);
            case BOX, SPHERE, CAPSULE -> new BakeResult(primitiveSettings(node, resolved), List.of());
            case AUTO -> {
                if (usage == CollisionUsage.STATIC) {
                    yield new BakeResult(meshSettings(geometry), List.of(geometry));
                } else {
                    yield vhacdResult(geometry, usage);
                }
            }
        };
    }

    public boolean supportsGeometryNode(String typeId) {
        return geometrySources.supports(typeId);
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

    private static boolean isPrimitiveStrategy(CollisionStrategy strategy) {
        return strategy == CollisionStrategy.BOX
                || strategy == CollisionStrategy.SPHERE
                || strategy == CollisionStrategy.CAPSULE;
    }

    private static CollisionStrategy strategyForNode(Node node, String typeId, CollisionUsage usage) {
        if ("RigidBody3D".equals(typeId) || "StaticBody3D".equals(typeId)) {
            return CollisionStrategy.parse(node.getProperty("shape"), CollisionStrategy.AUTO);
        }
        return CollisionStrategy.parse(node.getProperty("collision_strategy"),
                usage == CollisionUsage.STATIC ? CollisionStrategy.AUTO : CollisionStrategy.CONVEX);
    }

    private static CollisionStrategy resolveAutoStrategy(CollisionStrategy requested, CollisionUsage usage, CollisionGeometry geometry) {
        if (requested != CollisionStrategy.AUTO) {
            return requested;
        }
        int vertexCount = geometry.vertices().length / 3;
        if (usage == CollisionUsage.DYNAMIC && vertexCount <= 48) {
            return CollisionStrategy.CONVEX;
        }
        return usage == CollisionUsage.STATIC ? CollisionStrategy.MESH : CollisionStrategy.VHACD;
    }

    private static ShapeSettings primitiveSettings(Node node, CollisionStrategy strategy) {
        return switch (strategy) {
            case SPHERE -> new SphereShapeSettings((float) Math.max(0.01, SceneNodeTransform.parseDouble(node.getProperty("radius"), 0.5)));
            case CAPSULE -> {
                double radius = Math.max(0.01, SceneNodeTransform.parseDouble(node.getProperty("radius"), 0.3));
                double height = Math.max(radius * 2.0, SceneNodeTransform.parseDouble(node.getProperty("height"), 1.8));
                yield new CapsuleShapeSettings((float) Math.max(MIN_CAPSULE_HALF_HEIGHT, height * 0.5 - radius), (float) radius);
            }
            default -> new BoxShapeSettings(
                    (float) Math.max(0.01, SceneNodeTransform.parseDouble(node.getProperty("sx"), 1.0) * 0.5),
                    (float) Math.max(0.01, SceneNodeTransform.parseDouble(node.getProperty("sy"), 1.0) * 0.5),
                    (float) Math.max(0.01, SceneNodeTransform.parseDouble(node.getProperty("sz"), 1.0) * 0.5)
            );
        };
    }

    private static ShapeSettings convexSettings(CollisionGeometry geometry) {
        return new ConvexHullShapeSettings(Arrays.asList(float3s(geometry.vertices())));
    }

    private static ShapeSettings meshSettings(CollisionGeometry geometry) {
        return new MeshShapeSettings(float3s(geometry.vertices()), indexedTriangles(geometry.indices()));
    }

    private BakeResult vhacdResult(CollisionGeometry geometry, CollisionUsage usage) {
        Parameters parameters = new Parameters()
                .setMaxConvexHulls(usage == CollisionUsage.DYNAMIC ? 16 : 24)
                .setMaxNumVerticesPerCh(32)
                .setResolution(usage == CollisionUsage.DYNAMIC ? 100_000 : 200_000)
                .setShrinkWrap(false); // shrink-wrap produces sliver hulls that fail collision
        try (parameters; Decomposer decomposer = new Decomposer()) {
            var hulls = decomposer.decompose(geometry.vertices(), geometry.indices(), parameters);
            if (hulls == null || hulls.isEmpty()) {
                return new BakeResult(convexSettings(geometry), List.of(geometry));
            }

            Vec3 zero = Vec3.sZero();
            Quat identity = Quat.sIdentity();

            List<CollisionGeometry> resultHulls = new ArrayList<>();
            ShapeSettings compoundSettings;

            if (usage == CollisionUsage.STATIC) {
                StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
                for (ConvexHull hull : hulls) {
                    try {
                        int pointCount = hull.countPoints();
                        float[] points = new float[pointCount * 3];
                        hull.getPointsAsBuffer().get(points);
                        resultHulls.add(new CollisionGeometry(points, new int[0]));
                        settings.addShape(zero, identity, new ConvexHullShapeSettings(Arrays.asList(float3s(points))));
                    } finally {
                        hull.close();
                    }
                }
                compoundSettings = settings;
            } else {
                MutableCompoundShapeSettings settings = new MutableCompoundShapeSettings();
                for (ConvexHull hull : hulls) {
                    try {
                        int pointCount = hull.countPoints();
                        float[] points = new float[pointCount * 3];
                        hull.getPointsAsBuffer().get(points);
                        resultHulls.add(new CollisionGeometry(points, new int[0]));
                        settings.addShape(zero, identity, new ConvexHullShapeSettings(Arrays.asList(float3s(points))));
                    } finally {
                        hull.close();
                    }
                }
                compoundSettings = settings;
            }
            return new BakeResult(compoundSettings, resultHulls);
        } catch (Exception e) {
            System.err.println("[moud] VHACD decomposition failed: " + e.getMessage());
            return new BakeResult(convexSettings(geometry), List.of(geometry));
        }
    }


    private static Float3[] float3s(float[] vertices) {
        int count = vertices == null ? 0 : vertices.length / 3;
        Float3[] out = new Float3[count];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            out[i] = new Float3(vertices[base], vertices[base + 1], vertices[base + 2]);
        }
        return out;
    }

    private static IndexedTriangle[] indexedTriangles(int[] indices) {
        int count = indices == null ? 0 : indices.length / 3;
        IndexedTriangle[] out = new IndexedTriangle[count];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            out[i] = new IndexedTriangle(indices[base], indices[base + 1], indices[base + 2], 0);
        }
        return out;
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
