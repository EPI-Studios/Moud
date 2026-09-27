package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.ModelFormat;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshConverter;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshReader;
import com.meekdev.amnetic.client.model.internal.parse.BbmodelParser;
import com.meekdev.moud.core.asset.BbmodelImport;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.mesh.EditableMesh;
import com.meekdev.moud.core.mesh.EditableMesh.Kind;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.script.api.MeshesRef;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class MeshSources implements MeshesRef {

    public static final MeshSources INSTANCE = new MeshSources();

    private static final int POSITION_OFFSET = 0;
    private static final int NORMAL_OFFSET = 3;
    private static final int UV_OFFSET = 6;

    private record Point(float x, float y, float z) {
        static Point of(Vector3f position) {
            return new Point(plainZero(position.x), plainZero(position.y), plainZero(position.z));
        }

        private static float plainZero(float value) {
            return value == 0 ? 0f : value;
        }
    }

    private record VertexIndices(int[] positions, int[] normals, int[] uvs) {
        VertexIndices(int count) {
            this(new int[count], new int[count], new int[count]);
        }
    }

    private MeshSources() {}

    @Override
    public CompletableFuture<EditableMesh> load(String source) {
        if (!source.startsWith(Res.SCHEME) || source.contains("#")) {
            return refuse("meshes.load reads a res:// model file or an editable mesh, not " + source);
        }
        ModelFormat format = ModelFormat.fromPath(source);
        if (format == null || format == ModelFormat.OBJ) {
            return refuse(source + " is not a glTF, ammesh or bbmodel file");
        }
        byte[] bytes = PlaceFiles.read(source);
        if (bytes == null) {
            return refuse("there is no " + source);
        }
        return CompletableFuture.supplyAsync(() -> fromModel(parse(bytes, format, source)));
    }

    private static CompletableFuture<EditableMesh> refuse(String message) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(message));
    }

    private static ModelIR parse(byte[] bytes, ModelFormat format, String source) {
        return switch (format) {
            case GLTF -> AmmeshReader.read(AmmeshConverter.convert(bytes, PlaceFiles.idOf(source)));
            case BBMODEL -> BbmodelParser.parse(modernBbmodel(bytes), source);
            default -> AmmeshReader.read(bytes);
        };
    }

    private static byte[] modernBbmodel(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        return BbmodelImport.modernize(json).getBytes(StandardCharsets.UTF_8);
    }

    static EditableMesh fromModel(ModelIR model) {
        EditableMesh mesh = new EditableMesh();
        Map<Point, Integer> welded = new HashMap<>();
        for (ModelIR.Part part : model.parts()) addPart(mesh, part, model.materials(), welded);
        mesh.removeUnused();
        return mesh;
    }

    private static void addPart(EditableMesh mesh, ModelIR.Part part, List<ModelIR.Material> materials, Map<Point, Integer> welded) {
        VertexIndices indices = addVertices(mesh, part, welded);
        int color = addColor(mesh, part, materials);
        addTriangles(mesh, part, indices, color);
    }

    private static VertexIndices addVertices(EditableMesh mesh, ModelIR.Part part, Map<Point, Integer> welded) {
        int count = part.vertexCount();
        VertexIndices indices = new VertexIndices(count);
        Matrix3f normalTransform = part.transform.normal(new Matrix3f());
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        float[] data = part.vertices;
        for (int n = 0; n < count; n++) {
            int base = n * ModelIR.VERTEX_STRIDE_FLOATS;
            int positionAt = base + POSITION_OFFSET;
            int normalAt = base + NORMAL_OFFSET;
            int uvAt = base + UV_OFFSET;
            part.transform.transformPosition(position.set(data[positionAt], data[positionAt + 1], data[positionAt + 2]));
            normalTransform.transform(normal.set(data[normalAt], data[normalAt + 1], data[normalAt + 2])).normalize();
            indices.positions()[n] = weld(mesh, welded, position);
            indices.normals()[n] = mesh.addNormal(new Vector3(normal.x, normal.y, normal.z));
            indices.uvs()[n] = mesh.addUV(data[uvAt], data[uvAt + 1]);
        }
        return indices;
    }

    private static void addTriangles(EditableMesh mesh, ModelIR.Part part, VertexIndices indices, int color) {
        int[] positions = indices.positions();
        int[] normals = indices.normals();
        int[] uvs = indices.uvs();
        int corners = part.hasIndices() ? part.indices.length : part.vertexCount();
        for (int first = 0; first + 2 < corners; first += 3) {
            int a = corner(part, first);
            int b = corner(part, first + 1);
            int c = corner(part, first + 2);
            if (positions[a] == positions[b] || positions[b] == positions[c] || positions[a] == positions[c]) continue;
            int face = mesh.addTriangle(positions[a], positions[b], positions[c]);
            mesh.face(Kind.NORMAL, face, new int[] {normals[a], normals[b], normals[c]});
            mesh.face(Kind.UV, face, new int[] {uvs[a], uvs[b], uvs[c]});
            mesh.face(Kind.COLOR, face, new int[] {color, color, color});
        }
    }

    private static int addColor(EditableMesh mesh, ModelIR.Part part, List<ModelIR.Material> materials) {
        boolean known = part.materialIndex >= 0 && part.materialIndex < materials.size();
        ModelIR.Material material = known ? materials.get(part.materialIndex) : null;
        if (material == null) return mesh.addColor(Color.WHITE, 1);
        return mesh.addColor(new Color(material.baseR, material.baseG, material.baseB), material.baseA);
    }

    private static int corner(ModelIR.Part part, int n) {
        return part.hasIndices() ? part.indices[n] : n;
    }

    private static int weld(EditableMesh mesh, Map<Point, Integer> welded, Vector3f position) {
        Point point = Point.of(position);
        Integer known = welded.get(point);
        if (known != null) return known;
        int added = mesh.addVertex(new Vector3(position.x, position.y, position.z));
        welded.put(point, added);
        return added;
    }
}
