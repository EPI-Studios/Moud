package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.mesh.EditableMesh;
import com.meekdev.moud.core.mesh.MeshStore;
import com.meekdev.moud.mod.MoudMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class EditableModels {

    private record Built(EditableMesh mesh, @Nullable Model model, int version) {}

    private static final Map<String, Built> BUILT = new HashMap<>();
    private static final Set<Model> OURS = Collections.newSetFromMap(new IdentityHashMap<>());

    private EditableModels() {}

    static boolean owns(Model model) {
        return OURS.contains(model);
    }

    static @Nullable Model of(String uri, String slot) {
        EditableMesh mesh = MeshStore.client().find(uri);
        Built built = BUILT.get(slot);
        if (mesh == null) {
            if (built != null) drop(slot, built);
            return null;
        }
        if (built != null && built.mesh() == mesh && built.version() == mesh.version()) return built.model();
        Model made;
        try {
            made = build(mesh);
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("editable mesh {} cannot be drawn: {}", uri, e.toString());
            return built == null ? null : built.model();
        }
        if (built != null && built.model() != null) release(built.model());
        BUILT.put(slot, new Built(mesh, made, mesh.version()));
        return made;
    }

    public static void sweep() {
        for (Iterator<Map.Entry<String, Built>> it = BUILT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Built> entry = it.next();
            String uri = MeshLooks.file(entry.getKey());
            if (MeshStore.client().find(uri) == entry.getValue().mesh()) continue;
            if (entry.getValue().model() != null) release(entry.getValue().model());
            it.remove();
        }
    }

    private static void drop(String slot, Built built) {
        if (built.model() != null) release(built.model());
        BUILT.remove(slot);
    }

    private static void release(Model model) {
        OURS.remove(model);
        model.dispose();
    }

    private static @Nullable Model build(EditableMesh mesh) {
        EditableMesh.Baked baked = mesh.bake();
        if (baked.faces() == 0) return null;
        Map<Tint, List<Integer>> facesByTint = new LinkedHashMap<>();
        for (int face = 0; face < baked.faces(); face++) {
            facesByTint.computeIfAbsent(Tint.of(baked.color(face)), ignored -> new ArrayList<>()).add(face);
        }

        ModelIR ir = new ModelIR();
        ir.setName("editable mesh");
        for (Map.Entry<Tint, List<Integer>> group : facesByTint.entrySet()) {
            int material = ir.addMaterial(group.getKey().material());
            List<Integer> faces = group.getValue();
            float[] vertices = vertices(baked, faces);
            int[] indices = new int[faces.size() * 3];
            for (int n = 0; n < indices.length; n++) indices[n] = n;
            ir.addPart(new ModelIR.Part(vertices, null, indices, material, -1, null, "mesh"));
        }

        Model model = Models.fromIR(ir);
        OURS.add(model);
        return model;
    }

    private static float[] vertices(EditableMesh.Baked baked, List<Integer> faces) {
        float[] vertices = new float[faces.size() * 3 * ModelIR.VERTEX_STRIDE_FLOATS];
        int at = 0;
        for (int face : faces) {
            for (int corner = 0; corner < 3; corner++) {
                at = put(vertices, at, baked.position(face, corner));
                at = put(vertices, at, baked.normal(face, corner));
                vertices[at++] = (float) baked.u(face, corner);
                vertices[at++] = (float) baked.v(face, corner);
            }
        }
        return vertices;
    }

    private static int put(float[] into, int at, Vector3 value) {
        into[at] = (float) value.x();
        into[at + 1] = (float) value.y();
        into[at + 2] = (float) value.z();
        return at + 3;
    }

    private record Tint(int red, int green, int blue, int alpha) {

        static Tint of(Color color) {
            return new Tint(channel(color.r()), channel(color.g()), channel(color.b()), channel(color.a()));
        }

        ModelIR.Material material() {
            ModelIR.Material material = new ModelIR.Material();
            material.name = "mesh";
            material.baseR = red / 255f;
            material.baseG = green / 255f;
            material.baseB = blue / 255f;
            material.baseA = alpha / 255f;
            material.blend = material.baseA < 1;
            return material;
        }

        private static int channel(float value) {
            return Math.round(Math.clamp(value, 0f, 1f) * 255);
        }
    }
}
