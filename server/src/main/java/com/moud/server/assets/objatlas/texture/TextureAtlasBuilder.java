package com.moud.server.assets.objatlas.texture;

import com.moud.server.assets.objatlas.obj.ObjMaterial;
import com.moud.server.assets.objatlas.obj.ObjModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Build a simple vertical texture atlas from all diffuse textures (map_Kd) used in the ObjModel.
 *
 * Layout:
 *  - All textures stacked vertically.
 *  - atlasWidth  = max(textureWidth)
 *  - atlasHeight = sum(textureHeight)
 *
 * UV remap logic (for a given texture):
 *  U' = uOffset + U * uScale
 *  V' = vOffset + V * vScale
 */
public final class TextureAtlasBuilder {

    /**
     * Build an atlas and write it to disk.
     *
     * @param model           Parsed OBJ model (with materials).
     * @param baseDir         Directory where original textures are located (usually objPath.getParent()).
     * @param outputAtlasPath Path where the atlas image will be written (e.g. baseDir.resolve("atlas_diffuse.png")).
     * @return TextureAtlas information (regions + atlas size).
     */
    public TextureAtlas buildAtlas(ObjModel model, Path baseDir, Path outputAtlasPath) throws IOException {
        Map<String, BufferedImage> imagesByTextureName = new LinkedHashMap<>();

        // 1) Collect all diffuse texture names from materials
        for (ObjMaterial mat : model.materials().values()) {
            String mapKd = mat.mapDiffuse();
            if (mapKd == null || mapKd.isBlank()) {
                continue;
            }
            if (imagesByTextureName.containsKey(mapKd)) {
                continue; // already loaded
            }

            Path texturePath = baseDir.resolve(mapKd);
            if (!Files.exists(texturePath)) {
                System.err.println("[TextureAtlasBuilder] Missing texture: " + texturePath);
                continue;
            }

            BufferedImage img = ImageIO.read(texturePath.toFile());
            if (img == null) {
                System.err.println("[TextureAtlasBuilder] Cannot read texture as image: " + texturePath);
                continue;
            }

            imagesByTextureName.put(mapKd, img);
        }

        if (imagesByTextureName.isEmpty()) {
            throw new IOException("No diffuse textures (map_Kd) found to build an atlas.");
        }

        // 2) Compute atlas dimensions
        int atlasWidth = 0;
        int atlasHeight = 0;

        for (BufferedImage img : imagesByTextureName.values()) {
            atlasWidth = Math.max(atlasWidth, img.getWidth());
            atlasHeight += img.getHeight();
        }

        System.out.printf(Locale.ROOT,
                "[TextureAtlasBuilder] Building atlas %dx%d from %d textures%n",
                atlasWidth, atlasHeight, imagesByTextureName.size());

        // 3) Create atlas image
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);

        Map<String, TextureRegion> regions = new LinkedHashMap<>();

        int currentY = 0;
        for (Map.Entry<String, BufferedImage> entry : imagesByTextureName.entrySet()) {
            String texName = entry.getKey();
            BufferedImage img = entry.getValue();

            int texWidth = img.getWidth();
            int texHeight = img.getHeight();

            // Copy into atlas at x=0, y=currentY
            for (int y = 0; y < texHeight; y++) {
                for (int x = 0; x < texWidth; x++) {
                    int argb = img.getRGB(x, y);
                    atlas.setRGB(x, currentY + y, argb);
                }
            }

            // Normalized region infos
            float uOffset = 0.0f;
            float vOffset = (float) currentY / (float) atlasHeight;
            float uScale = (float) texWidth / (float) atlasWidth;
            float vScale = (float) texHeight / (float) atlasHeight;

            TextureRegion region = new TextureRegion(
                    0,
                    currentY,
                    texWidth,
                    texHeight,
                    uOffset,
                    vOffset,
                    uScale,
                    vScale
            );
            regions.put(texName, region);

            currentY += texHeight;
        }

        // 4) Save atlas
        if (outputAtlasPath.getParent() != null) {
            Files.createDirectories(outputAtlasPath.getParent());
        }
        String format = getImageFormatFromFileName(outputAtlasPath);
        ImageIO.write(atlas, format, outputAtlasPath.toFile());

        String atlasFileName = outputAtlasPath.getFileName().toString();
        return new TextureAtlas(atlasFileName, atlasWidth, atlasHeight, Map.copyOf(regions));
    }

    private String getImageFormatFromFileName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return "png";
    }
}
