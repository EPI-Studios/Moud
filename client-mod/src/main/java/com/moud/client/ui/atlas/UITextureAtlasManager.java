package com.moud.client.ui.atlas;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public final class UITextureAtlasManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UITextureAtlasManager.class);
    private static final UITextureAtlasManager INSTANCE = new UITextureAtlasManager();

    private final Map<Identifier, AtlasData> atlasCache = new ConcurrentHashMap<>();

    private UITextureAtlasManager() {}

    public static UITextureAtlasManager getInstance() {
        return INSTANCE;
    }

    public AtlasRegion getRegion(String atlasPath, String subTextureName) {
        if (atlasPath == null || subTextureName == null) {
            return null;
        }

        Identifier atlasId = Identifier.tryParse(atlasPath);
        if (atlasId == null) {
            LOGGER.warn("Invalid atlas identifier provided: {}", atlasPath);
            return null;
        }

        AtlasData atlas = atlasCache.get(atlasId);
        if (atlas == null) {
            atlas = loadAtlas(atlasId);
            if (atlas == null) {
                return null;
            }
            atlasCache.put(atlasId, atlas);
        }

        AtlasRegion region = atlas.regions().get(subTextureName);
        if (region == null) {
            LOGGER.warn("SubTexture '{}' not found in atlas {}", subTextureName, atlasId);
        }
        return region;
    }

    public void clear() {
        atlasCache.clear();
    }

    private AtlasData loadAtlas(Identifier atlasId) {
        try {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            Optional<Resource> xmlResource = resourceManager.getResource(atlasId);
            if (xmlResource.isEmpty()) {
                LOGGER.warn("Texture atlas {} not found in resource manager", atlasId);
                return null;
            }

            Document document;
            try (InputStream stream = xmlResource.get().getInputStream()) {
                document = parseXml(stream);
            }

            if (document == null) {
                return null;
            }

            Element root = document.getDocumentElement();
            if (root == null || !"TextureAtlas".equalsIgnoreCase(root.getNodeName())) {
                LOGGER.warn("Texture atlas {} has unexpected root element", atlasId);
                return null;
            }

            String imagePath = root.getAttribute("imagePath");
            if (imagePath == null || imagePath.isBlank()) {
                LOGGER.warn("Texture atlas {} missing imagePath attribute", atlasId);
                return null;
            }

            Identifier textureId = resolveTextureIdentifier(atlasId, imagePath);
            AtlasDimensions dimensions = readAtlasDimensions(textureId);
            if (dimensions == null) {
                return null;
            }

            Map<String, AtlasRegion> regions = new ConcurrentHashMap<>();
            NodeList subTextures = root.getElementsByTagName("SubTexture");
            for (int i = 0; i < subTextures.getLength(); i++) {
                Node node = subTextures.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element element = (Element) node;
                String name = element.getAttribute("name");
                if (name == null || name.isBlank()) {
                    continue;
                }

                Integer x = parseIntAttribute(element, "x");
                Integer y = parseIntAttribute(element, "y");
                Integer width = parseIntAttribute(element, "width");
                Integer height = parseIntAttribute(element, "height");
                if (x == null || y == null || width == null || height == null) {
                    LOGGER.warn("Skipping SubTexture '{}' in atlas {} due to missing coordinates", name, atlasId);
                    continue;
                }

                int frameX = parseIntAttribute(element, "frameX", 0);
                int frameY = parseIntAttribute(element, "frameY", 0);
                int frameWidth = parseIntAttribute(element, "frameWidth", width);
                int frameHeight = parseIntAttribute(element, "frameHeight", height);

                AtlasRegion region = new AtlasRegion(
                        textureId,
                        x,
                        y,
                        width,
                        height,
                        frameX,
                        frameY,
                        frameWidth,
                        frameHeight,
                        dimensions.width(),
                        dimensions.height()
                );
                regions.put(name, region);
            }

            if (regions.isEmpty()) {
                LOGGER.warn("Texture atlas {} did not define any SubTexture entries", atlasId);
            } else {
                LOGGER.info("Loaded {} sub-textures from atlas {}", regions.size(), atlasId);
            }

            return new AtlasData(textureId, dimensions.width(), dimensions.height(), regions);
        } catch (Exception e) {
            LOGGER.error("Failed to load texture atlas {}", atlasId, e);
            return null;
        }
    }

    private Document parseXml(InputStream stream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(stream);
        } catch (Exception e) {
            LOGGER.error("Failed to parse texture atlas XML", e);
            return null;
        }
    }

    private Identifier resolveTextureIdentifier(Identifier atlasId, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String normalizedPath = imagePath.trim().replace(" ", "_");
        boolean hasExplicitNamespace = normalizedPath.contains(":");

        if (hasExplicitNamespace) {
            Identifier parsed = Identifier.tryParse(normalizedPath.toLowerCase());
            if (parsed != null) {
                return parsed;
            }
        }

        String atlasPath = atlasId.getPath();
        int lastSlash = atlasPath.lastIndexOf('/');
        String baseDir = lastSlash >= 0 ? atlasPath.substring(0, lastSlash + 1) : "";
        String relativePath = normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath;

        Identifier relativeId = Identifier.tryParse(atlasId.getNamespace() + ":" + baseDir + relativePath.toLowerCase());
        if (relativeId != null) {
            return relativeId;
        }

        LOGGER.error("Failed to resolve texture identifier from imagePath '{}' in atlas {}", imagePath, atlasId);
        return null;
    }

    private AtlasDimensions readAtlasDimensions(Identifier textureId) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        try {
            Optional<Resource> textureResource = resourceManager.getResource(textureId);
            if (textureResource.isEmpty()) {
                LOGGER.warn("Atlas texture {} not found", textureId);
                return null;
            }

            try (InputStream stream = textureResource.get().getInputStream();
                 NativeImage image = NativeImage.read(stream)) {
                return new AtlasDimensions(image.getWidth(), image.getHeight());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read atlas texture {}", textureId, e);
            return null;
        }
    }

    private Integer parseIntAttribute(Element element, String attr) {
        String value = element.getAttribute(attr);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer for attribute '{}' on {}", attr, element.getTagName());
            return null;
        }
    }

    private int parseIntAttribute(Element element, String attr, int defaultValue) {
        Integer parsed = parseIntAttribute(element, attr);
        return parsed != null ? parsed : defaultValue;
    }

    public record AtlasRegion(
            Identifier textureId,
            int u,
            int v,
            int width,
            int height,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight,
            int atlasWidth,
            int atlasHeight
    ) {}

    private record AtlasData(
            Identifier textureId,
            int atlasWidth,
            int atlasHeight,
            Map<String, AtlasRegion> regions
    ) {}

    private record AtlasDimensions(int width, int height) {}
}
