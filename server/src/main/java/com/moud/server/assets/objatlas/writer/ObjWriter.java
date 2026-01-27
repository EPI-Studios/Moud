package com.moud.server.assets.objatlas.writer;

import com.moud.api.math.Vector2;
import com.moud.api.math.Vector3;
import com.moud.server.assets.objatlas.obj.ObjFace;
import com.moud.server.assets.objatlas.obj.ObjModel;
import com.moud.server.assets.objatlas.obj.ObjVertexRef;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes an ObjModel to an OBJ file.
 */
public final class ObjWriter {

    public void write(ObjModel model, Path outputPath, String mtlFileName) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            if (mtlFileName != null && !mtlFileName.isBlank()) {
                writer.write("mtllib " + mtlFileName);
                writer.newLine();
            }

            if (model.name() != null && !model.name().isBlank()) {
                writer.write("o " + model.name());
                writer.newLine();
            }

            // v
            for (Vector3 p : model.positions()) {
                writer.write(String.format(Locale.ROOT, "v %f %f %f%n", p.x, p.y, p.z));
            }

            // vt
            for (Vector2 t : model.texCoords()) {
                writer.write(String.format(Locale.ROOT, "vt %f %f%n", t.x, t.y));
            }

            // vn
            for (Vector3 n : model.normals()) {
                writer.write(String.format(Locale.ROOT, "vn %f %f %f%n", n.x, n.y, n.z));
            }

            // Faces + groups/materials/smoothing
            String currentGroup = null;
            String currentMaterial = null;
            int currentSmooth = Integer.MIN_VALUE;

            for (ObjFace face : model.faces()) {
                if (!Objects.equals(face.groupName(), currentGroup)) {
                    currentGroup = face.groupName();
                    if (currentGroup != null && !currentGroup.isBlank()) {
                        writer.write("g " + currentGroup);
                    } else {
                        writer.write("g");
                    }
                    writer.newLine();
                }

                if (!Objects.equals(face.materialName(), currentMaterial)) {
                    currentMaterial = face.materialName();
                    if (currentMaterial != null && !currentMaterial.isBlank()) {
                        writer.write("usemtl " + currentMaterial);
                        writer.newLine();
                    }
                }

                if (face.smoothingGroup() != currentSmooth) {
                    currentSmooth = face.smoothingGroup();
                    if (currentSmooth <= 0) {
                        writer.write("s off");
                    } else {
                        writer.write("s " + currentSmooth);
                    }
                    writer.newLine();
                }

                writer.write("f");
                for (ObjVertexRef vRef : face.vertices()) {
                    int v = vRef.positionIndex() + 1;
                    int vt = vRef.texCoordIndex() + 1;
                    int vn = vRef.normalIndex() + 1;

                    writer.write(" ");
                    if (vRef.texCoordIndex() >= 0 && vRef.normalIndex() >= 0) {
                        writer.write(v + "/" + vt + "/" + vn);
                    } else if (vRef.texCoordIndex() >= 0) {
                        writer.write(v + "/" + vt);
                    } else if (vRef.normalIndex() >= 0) {
                        writer.write(v + "//" + vn);
                    } else {
                        writer.write(Integer.toString(v));
                    }
                }
                writer.newLine();
            }
        }
    }
}
