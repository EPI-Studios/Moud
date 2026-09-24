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

    private MeshSources() {}

    @Override
    public CompletableFuture<EditableMesh> load(String source) {
        if (!source.startsWith(Res.SCHEME) || source.contains("#")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("meshes.load reads a res:// model file or an editable mesh, not " + source));
        }
        ModelFormat format = ModelFormat.fromPath(source);
        if (format == null || format == ModelFormat.OBJ) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(source + " is not a glTF, ammesh or bbmodel file"));
        }
        byte[] bytes = PlaceFiles.read(source);
        if (bytes == null) return CompletableFuture.failedFuture(new IllegalArgumentException("there is no " + source));
        return CompletableFuture.supplyAsync(() -> fromModel(parse(bytes, format, source)));
    }

    private static ModelIR parse(byte[] bytes, ModelFormat format, String source) {
        return switch (format) {
            case GLTF -> AmmeshReader.read(AmmeshConverter.convert(bytes, PlaceFiles.idOf(source)));
            case BBMODEL -> BbmodelParser.parse(BbmodelImport.modernize(new String(bytes, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8), source);
            default -> AmmeshReader.read(bytes);
        };
    }

    static EditableMesh fromModel(ModelIR model) {
        EditableMesh mesh = new EditableMesh();
        Map<List<Float>, Integer> vertexAt = new HashMap<>();
        for (ModelIR.Part part : model.parts()) addPart(mesh, part, model.materials(), vertexAt);
        mesh.removeUnused();
        return mesh;
    }

    private static void addPart(EditableMesh mesh, ModelIR.Part part, List<ModelIR.Material> materials, Map<List<Float>, Integer> vertexAt) {
        int count = part.vertexCount();
        int[] vertex = new int[count];
        int[] normal = new int[count];
        int[] uv = new int[count];
        Matrix3f turn = part.transform.normal(new Matrix3f());
        Vector3f position = new Vector3f();
        Vector3f facing = new Vector3f();
        float[] data = part.vertices;
        for (int n = 0; n < count; n++) {
            int base = n * ModelIR.VERTEX_STRIDE_FLOATS;
            part.transform.transformPosition(position.set(data[base], data[base + 1], data[base + 2]));
            turn.transform(facing.set(data[base + 3], data[base + 4], data[base + 5])).normalize();
            vertex[n] = weld(mesh, vertexAt, position);
            normal[n] = mesh.addNormal(new Vector3(facing.x, facing.y, facing.z));
            uv[n] = mesh.addUV(data[base + 6], data[base + 7]);
        }

        int color = addColor(mesh, part, materials);
        int corners = part.hasIndices() ? part.indices.length : count;
        for (int first = 0; first + 2 < corners; first += 3) {
            int a = corner(part, first);
            int b = corner(part, first + 1);
            int c = corner(part, first + 2);
            if (vertex[a] == vertex[b] || vertex[b] == vertex[c] || vertex[a] == vertex[c]) continue;
            int face = mesh.addTriangle(vertex[a], vertex[b], vertex[c]);
            mesh.face(Kind.NORMAL, face, new int[] {normal[a], normal[b], normal[c]});
            mesh.face(Kind.UV, face, new int[] {uv[a], uv[b], uv[c]});
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

    private static int weld(EditableMesh mesh, Map<List<Float>, Integer> vertexAt, Vector3f position) {
        List<Float> key = weldKey(position);
        Integer known = vertexAt.get(key);
        if (known != null) return known;
        int made = mesh.addVertex(new Vector3(position.x, position.y, position.z));
        vertexAt.put(key, made);
        return made;
    }

    private static List<Float> weldKey(Vector3f position) {
        return List.of(plainZero(position.x), plainZero(position.y), plainZero(position.z));
    }

    private static float plainZero(float value) {
        return value == 0 ? 0f : value;
    }
}
