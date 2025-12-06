package com.moud.server.assets.objatlas.writer;

import com.moud.server.assets.objatlas.obj.ObjMaterial;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Writes materials to an MTL file.
 */
public final class MtlWriter {

    public void write(Map<String, ObjMaterial> materials, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (ObjMaterial mat : materials.values()) {
                writer.write("newmtl " + mat.name());
                writer.newLine();

                if (mat.ambient() != null) {
                    writer.write(String.format(Locale.ROOT, "Ka %f %f %f%n",
                            mat.ambient().r(), mat.ambient().g(), mat.ambient().b()));
                }
                if (mat.diffuse() != null) {
                    writer.write(String.format(Locale.ROOT, "Kd %f %f %f%n",
                            mat.diffuse().r(), mat.diffuse().g(), mat.diffuse().b()));
                }
                if (mat.specular() != null) {
                    writer.write(String.format(Locale.ROOT, "Ks %f %f %f%n",
                            mat.specular().r(), mat.specular().g(), mat.specular().b()));
                }
                if (mat.emissive() != null) {
                    writer.write(String.format(Locale.ROOT, "Ke %f %f %f%n",
                            mat.emissive().r(), mat.emissive().g(), mat.emissive().b()));
                }

                if (mat.shininess() != 0.0f) {
                    writer.write(String.format(Locale.ROOT, "Ns %f%n", mat.shininess()));
                }
                if (mat.opticalDensity() != 1.0f) {
                    writer.write(String.format(Locale.ROOT, "Ni %f%n", mat.opticalDensity()));
                }

                writer.write(String.format(Locale.ROOT, "d %f%n", mat.dissolve()));
                writer.write(String.format(Locale.ROOT, "illum %d%n", mat.illuminationModel()));

                if (mat.mapAmbient() != null && !mat.mapAmbient().isBlank()) {
                    writer.write("map_Ka " + mat.mapAmbient());
                    writer.newLine();
                }
                if (mat.mapDiffuse() != null && !mat.mapDiffuse().isBlank()) {
                    writer.write("map_Kd " + mat.mapDiffuse());
                    writer.newLine();
                }
                if (mat.mapSpecular() != null && !mat.mapSpecular().isBlank()) {
                    writer.write("map_Ks " + mat.mapSpecular());
                    writer.newLine();
                }
                if (mat.mapBump() != null && !mat.mapBump().isBlank()) {
                    writer.write("map_Bump " + mat.mapBump());
                    writer.newLine();
                }

                writer.newLine();
            }
        }
    }
}
