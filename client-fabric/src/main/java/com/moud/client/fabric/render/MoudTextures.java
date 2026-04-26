package com.moud.client.fabric.render;

import com.moud.client.fabric.assets.AssetsClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoudTextures implements AssetsClient.Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudTex");
    public static final Identifier WHITE_ID = Identifier.of("moud", "dynamic/white");
    public static final Identifier BLACK_ID = Identifier.of("moud", "dynamic/black");
    public static final Identifier FLAT_NORMAL_ID = Identifier.of("moud", "dynamic/flat_normal");
    public static final Identifier ORM_DEFAULT_ID = Identifier.of("moud", "dynamic/orm_default");
    private static final TextureSize DEFAULT_SIZE = new TextureSize(1, 1);
    private static final TextureSize DEFAULT_WHITE_SIZE = new TextureSize(64, 64);
    private static final String DEFAULT_WHITE_RESOURCE = "/assets/moud/textures/dynamic/white.png";

    private static final int MAX_TEXTURE_SIZE = 2048;
    private static final Object LOCK = new Object();
    private static MoudTextures instance;

    private static AssetsClient assets;
    private static boolean defaultsRegistered;
    private static final Set<Identifier> rawReadyIds = ConcurrentHashMap.newKeySet();

    private static long lastManifestRequestMs;
    private static Map<ResPath, AssetMeta> metaByPath = Map.of();
    private static List<String> imagePaths = List.of();
    private static final Map<AssetHash, TextureEntry> texturesByHash = new HashMap<>();

    private MoudTextures() {
    }

    public static void init(AssetsClient assetsClient) {
        if (assetsClient == null) {
            return;
        }
        synchronized (LOCK) {
            assets = assetsClient;
            if (instance == null) {
                instance = new MoudTextures();
                assetsClient.addListener(instance);
            }
        }
        ensureDefaultsRegistered();
    }

    public static void clear() {
        synchronized (LOCK) {
            metaByPath = Map.of();
            imagePaths = List.of();
            lastManifestRequestMs = 0L;
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(MoudTextures::destroyAllTextures);
        } else {
            destroyAllTextures();
        }
    }

    private static void destroyAllTextures() {
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager tm = client == null ? null : client.getTextureManager();
        if (tm == null) {
            return;
        }
        synchronized (LOCK) {
            // keep static defaults alive for client lifetime, destroying them races samplers to mc missing-checker
            for (TextureEntry entry : texturesByHash.values()) {
                if (entry != null && entry.id != null) {
                    try {
                        tm.destroyTexture(entry.id);
                    } catch (Exception ignored) {
                    }
                }
            }
            texturesByHash.clear();
            rawReadyIds.clear();
        }
    }

    public static void registerRaw(Identifier id, byte[] pngBytes) {
        if (id == null || pngBytes == null || pngBytes.length == 0) return;
        rawReadyIds.remove(id);
        Thread.ofVirtual().name("moud-tex-decode").start(() -> decodeAndUploadRaw(id, pngBytes));
    }

    public static boolean isRawReady(Identifier id) {
        return id != null && rawReadyIds.contains(id);
    }

    private static void decodeAndUploadRaw(Identifier id, byte[] pngBytes) {
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(pngBytes));
        } catch (Exception e) {
            return;
        }
        int imgW = image.getWidth(), imgH = image.getHeight();
        if (imgW > MAX_TEXTURE_SIZE || imgH > MAX_TEXTURE_SIZE) {
            float scale = (float) MAX_TEXTURE_SIZE / Math.max(imgW, imgH);
            int newW = Math.max(1, Math.round(imgW * scale)), newH = Math.max(1, Math.round(imgH * scale));
            try {
                NativeImage scaled = new NativeImage(newW, newH, false);
                image.resizeSubRectTo(0, 0, imgW, imgH, scaled);
                image.close();
                image = scaled;
            } catch (Exception e) {
                image.close();
                return;
            }
        }
        NativeImage finalImage = image;
        RenderSystem.recordRenderCall(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager tm = client == null ? null : client.getTextureManager();
            if (tm == null) { finalImage.close(); return; }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(finalImage);
            tm.registerTexture(id, tex);
            tex.upload();
            rawReadyIds.add(id);
        });
    }

    public static List<String> imageAssetPaths() {
        synchronized (LOCK) {
            return imagePaths;
        }
    }

    public static Identifier resolve(String textureRef) {
        ensureDefaultsRegistered();
        if (textureRef == null || textureRef.isBlank()) {
            return WHITE_ID;
        }
        String ref = textureRef.trim();
        if (ref.startsWith(ResPath.SCHEME)) {
            return resolveResTexture(ref);
        }
        Identifier id = Identifier.tryParse(ref);
        if (id == null) {
            return TextureManager.MISSING_IDENTIFIER;
        }
        if ("moud".equals(id.getNamespace())) {
            Identifier assetTexture = resolveMoudAssetTexture(id);
            if (assetTexture != null) {
                return assetTexture;
            }
            return id;
        }
        String path = id.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png") && !path.endsWith(".jpg") && !path.endsWith(".jpeg")) {
            path = path + ".png";
        }
        return Identifier.of(id.getNamespace(), path);
    }

    private static Identifier resolveMoudAssetTexture(Identifier id) {
        String path = id == null ? null : id.getPath();
        if (path == null || !path.startsWith("asset/")) {
            return null;
        }

        String hashText = path.substring("asset/".length());
        int slash = hashText.indexOf('/');
        if (slash >= 0) {
            hashText = hashText.substring(0, slash);
        }
        int dot = hashText.indexOf('.');
        if (dot >= 0) {
            hashText = hashText.substring(0, dot);
        }
        if (!AssetHash.validate(hashText).ok()) {
            return TextureManager.MISSING_IDENTIFIER;
        }
        return resolveAssetHashTexture(new AssetHash(hashText));
    }

    public static Identifier white() {
        ensureDefaultsRegistered();
        return WHITE_ID;
    }

    public static Identifier black() {
        ensureDefaultsRegistered();
        return BLACK_ID;
    }

    public static Identifier flatNormal() {
        ensureDefaultsRegistered();
        return FLAT_NORMAL_ID;
    }

    public static Identifier ormDefault() {
        ensureDefaultsRegistered();
        return ORM_DEFAULT_ID;
    }

    public static Identifier defaultSamplerFor(String samplerName) {
        ensureDefaultsRegistered();
        if (samplerName == null || samplerName.isBlank()) {
            return WHITE_ID;
        }
        return switch (samplerName) {
            case "normal_texture" -> FLAT_NORMAL_ID;
            case "orm_texture" -> ORM_DEFAULT_ID;
            case "emission_texture" -> BLACK_ID;
            case "albedo_texture", "metallic_texture", "roughness_texture", "ao_texture", "heightmap_texture" -> WHITE_ID;
            default -> WHITE_ID;
        };
    }

    // bind once before reading the gl id so veil multi-bind sees a valid GL_TEXTURE_2D target, also bypasses mc auto-create for unregistered moud ids
    public static int boundGlId(Identifier id) {
        ensureDefaultsRegistered();
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager tm = client == null ? null : client.getTextureManager();
        if (tm == null) {
            return 0;
        }
        AbstractTexture tex;
        if (id == null) {
            tex = tm.getOrDefault(WHITE_ID, null);
        } else if ("moud".equals(id.getNamespace())) {
            AbstractTexture direct = tm.getOrDefault(id, null);
            tex = direct != null ? direct : tm.getOrDefault(WHITE_ID, null);
        } else {
            tex = tm.getTexture(id);
        }
        if (tex == null) {
            return 0;
        }
        if (RenderSystem.isOnRenderThread()) {
            tex.bindTexture();
        }
        return tex.getGlId();
    }

    public static TextureSize sizeOf(Identifier id) {
        if (id == null) {
            return DEFAULT_SIZE;
        }
        synchronized (LOCK) {
            if (WHITE_ID.equals(id)) {
                return DEFAULT_WHITE_SIZE;
            }
            if (BLACK_ID.equals(id) || FLAT_NORMAL_ID.equals(id) || ORM_DEFAULT_ID.equals(id)) {
                return DEFAULT_SIZE;
            }
            for (TextureEntry entry : texturesByHash.values()) {
                if (entry != null && id.equals(entry.id)) {
                    return entry.size;
                }
            }
        }
        return DEFAULT_SIZE;
    }

    private static Identifier resolveResTexture(String resPathRaw) {
        ResPath resPath;
        try {
            resPath = new ResPath(resPathRaw);
        } catch (Exception ignored) {
            return TextureManager.MISSING_IDENTIFIER;
        }

        AssetMeta meta;
        synchronized (LOCK) {
            meta = metaByPath.get(resPath);
        }
        if (meta == null) {
            // manifest not arrived yet, show white instead of mc missing-checker while assets sync
            maybeRequestManifest();
            return WHITE_ID;
        }

        AssetHash hash = meta.hash();
        if (hash == null) {
            return TextureManager.MISSING_IDENTIFIER;
        }

        TextureEntry entry;
        synchronized (LOCK) {
            entry = texturesByHash.get(hash);
            if (entry == null) {
                entry = new TextureEntry(hash, Identifier.of("moud", "asset/" + hash.hex()));
                texturesByHash.put(hash, entry);
            }
            if (entry.state == TextureState.READY) {
                return entry.id;
            }
            if (entry.state == TextureState.REQUESTED) {
                return WHITE_ID;
            }
            if (entry.state == TextureState.FAILED) {
                return TextureManager.MISSING_IDENTIFIER;
            }
            entry.state = TextureState.REQUESTED;
        }

        requestDownload(hash);
        return WHITE_ID;
    }

    private static Identifier resolveAssetHashTexture(AssetHash hash) {
        if (hash == null) {
            return TextureManager.MISSING_IDENTIFIER;
        }

        TextureEntry entry;
        synchronized (LOCK) {
            entry = texturesByHash.get(hash);
            if (entry == null) {
                entry = new TextureEntry(hash, Identifier.of("moud", "asset/" + hash.hex()));
                texturesByHash.put(hash, entry);
            }
            if (entry.state == TextureState.READY) {
                return entry.id;
            }
            if (entry.state == TextureState.REQUESTED) {
                return WHITE_ID;
            }
            if (entry.state == TextureState.FAILED) {
                return TextureManager.MISSING_IDENTIFIER;
            }
            entry.state = TextureState.REQUESTED;
        }

        requestDownload(hash);
        return WHITE_ID;
    }

    private static void maybeRequestManifest() {
        AssetsClient a;
        Session s;
        synchronized (LOCK) {
            a = assets;
        }
        if (a == null) {
            return;
        }
        s = ClientSessionBus.get();
        if (s == null || s.state() != SessionState.CONNECTED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastManifestRequestMs < 2_000L) {
            return;
        }
        lastManifestRequestMs = now;
        a.requestManifest(s);
    }

    private static void requestDownload(AssetHash hash) {
        AssetsClient a;
        synchronized (LOCK) {
            a = assets;
        }
        if (a == null || hash == null) {
            return;
        }
        Session s = ClientSessionBus.get();
        if (s == null || s.state() != SessionState.CONNECTED) {
            return;
        }
        a.download(s, hash);
    }

    private static void ensureDefaultsRegistered() {
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager tm = client == null ? null : client.getTextureManager();
        if (tm == null) {
            return;
        }

        AbstractTexture existing = tm.getOrDefault(WHITE_ID, null);
        if (existing instanceof NativeImageBackedTexture) {
            defaultsRegistered = true;
            return;
        }

        synchronized (LOCK) {
            if (defaultsRegistered && !RenderSystem.isOnRenderThread()) {
                return;
            }
            defaultsRegistered = true;
        }

        Runnable register = () -> {
            if (tm.getOrDefault(WHITE_ID, null) instanceof NativeImageBackedTexture) {
                return;
            }
            registerCheckerTexture(tm, WHITE_ID);
            registerSolidTexture(tm, BLACK_ID, 0xFF000000);
            registerSolidTexture(tm, FLAT_NORMAL_ID, 0xFFFF8080);
            registerSolidTexture(tm, ORM_DEFAULT_ID, 0xFF00FFFF);
        };

        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(register::run);
        } else {
            register.run();
        }
    }

    // no-albedo placeholder, 2px grey/white checker on 16x16 tile so untextured faces show the unset indicator
    private static void registerCheckerTexture(TextureManager tm, Identifier id) {
        final int size = 16;
        final int cell = 2;
        final int light = 0xFFFFFFFF;
        final int dark = 0xFFCCCCCC;
        NativeImageBackedTexture tex = new NativeImageBackedTexture(size, size, false);
        NativeImage img = tex.getImage();
        if (img != null) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean isDark = (((x / cell) + (y / cell)) & 1) == 0;
                    img.setColor(x, y, isDark ? dark : light);
                }
            }
        }
        tm.registerTexture(id, tex);
        tex.upload();
        tex.setFilter(false, false);
    }

    private static void registerSolidTexture(TextureManager tm, Identifier id, int color) {
        NativeImageBackedTexture tex = new NativeImageBackedTexture(1, 1, false);
        NativeImage img = tex.getImage();
        if (img != null) {
            img.setColor(0, 0, color);
        }
        tm.registerTexture(id, tex);
        tex.upload();
        // default min-filter wants mipmaps, without it the texture is incomplete and drivers return the magenta checker
        tex.setFilter(false, false);
    }

    private static void registerBundledTexture(TextureManager tm, Identifier id, String resourcePath) {
        try (InputStream stream = MoudTextures.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                registerSolidTexture(tm, id, 0xFFFFFFFF);
                return;
            }
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
            tm.registerTexture(id, tex);
            tex.upload();
        } catch (IOException e) {
            registerSolidTexture(tm, id, 0xFFFFFFFF);
        }
    }

    @Override
    public void onManifest(AssetManifestResponse response) {
        if (response == null || response.entries() == null) {
            return;
        }
        HashMap<ResPath, AssetMeta> nextMeta = new HashMap<>();
        ArrayList<String> images = new ArrayList<>();
        for (AssetManifestResponse.Entry entry : response.entries()) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                continue;
            }
            nextMeta.put(entry.path(), entry.meta());
            if (entry.meta().type() == AssetType.IMAGE) {
                images.add(entry.path().value());
            }
        }
        images.sort(String::compareTo);

        synchronized (LOCK) {
            Map<ResPath, AssetMeta> oldMeta = metaByPath;
            for (Map.Entry<ResPath, AssetMeta> e : nextMeta.entrySet()) {
                AssetMeta prev = oldMeta.get(e.getKey());
                if (prev != null && !prev.hash().equals(e.getValue().hash())) {
                    TextureEntry stale = texturesByHash.remove(prev.hash());
                    if (stale != null && stale.id != null) {
                        Identifier idToDestroy = stale.id;
                        RenderSystem.recordRenderCall(() -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc != null && mc.getTextureManager() != null) {
                                mc.getTextureManager().destroyTexture(idToDestroy);
                            }
                        });
                    }
                }
            }
            metaByPath = Map.copyOf(nextMeta);
            imagePaths = List.copyOf(images);
        }
    }

    @Override
    public void onDownloadComplete(AssetHash hash, AssetTransferStatus status, byte[] bytes, String message) {
        if (hash == null) {
            return;
        }

        boolean isImage = false;
        TextureEntry entry;
        synchronized (LOCK) {
            entry = texturesByHash.get(hash);
            if (entry == null) {
                for (AssetMeta meta : metaByPath.values()) {
                    if (hash.equals(meta.hash()) && meta.type() == AssetType.IMAGE) {
                        isImage = true;
                        break;
                    }
                }
            }
        }

        // skip non-image manifest entries so we don't try to decode scripts or audio as textures
        if (entry == null && !isImage) {
            return;
        }

        if (status != AssetTransferStatus.OK || bytes == null) {
            if (entry != null) {
                synchronized (LOCK) {
                    entry.state = TextureState.FAILED;
                    entry.error = message != null ? message : "Download failed";
                }
            }
            return;
        }

        synchronized (LOCK) {
            if (entry == null) {
                entry = texturesByHash.get(hash);
                if (entry == null) {
                    entry = new TextureEntry(hash, Identifier.of("moud", "asset/" + hash.hex()));
                    texturesByHash.put(hash, entry);
                }
            }
            if (entry.state == TextureState.READY) {
                return;
            }
        }

        TextureEntry finalEntry = entry;
        Thread.ofVirtual().name("moud-tex-decode").start(() -> decodeAndUpload(finalEntry, bytes));
    }

    private static void decodeAndUpload(TextureEntry entry, byte[] bytes) {
        LOGGER.info("[Moud] decode start hash={} bytes={}",
                entry.hash.hex().substring(0, 8), bytes.length);
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            LOGGER.warn("[Moud] decode FAILED hash={}: {}",
                    entry.hash.hex().substring(0, 8),
                    e.getMessage() == null ? "decode failed" : e.getMessage());
            synchronized (LOCK) {
                entry.state = TextureState.FAILED;
                entry.error = e.getMessage() == null ? "decode failed" : e.getMessage();
            }
            return;
        }

        int imgW = image.getWidth();
        int imgH = image.getHeight();
        if (imgW > MAX_TEXTURE_SIZE || imgH > MAX_TEXTURE_SIZE) {
            float scale = (float) MAX_TEXTURE_SIZE / Math.max(imgW, imgH);
            int newW = Math.max(1, Math.round(imgW * scale));
            int newH = Math.max(1, Math.round(imgH * scale));
            NativeImage scaled;
            try {
                scaled = new NativeImage(newW, newH, false);
                image.resizeSubRectTo(0, 0, imgW, imgH, scaled);
            } catch (Exception e) {
                image.close();
                synchronized (LOCK) {
                    entry.state = TextureState.FAILED;
                    entry.error = "texture too large (" + imgW + "x" + imgH + ")";
                }
                return;
            }
            image.close();
            image = scaled;
        }

        NativeImage finalImage = image;
        RenderSystem.recordRenderCall(() -> {
            // re-check, two concurrent decodes racing on the same hash would close the first's gl handle while a sampler still uses it
            synchronized (LOCK) {
                if (entry.state == TextureState.READY) {
                    finalImage.close();
                    return;
                }
            }
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager tm = client == null ? null : client.getTextureManager();
            if (tm == null) {
                finalImage.close();
                synchronized (LOCK) {
                    entry.state = TextureState.FAILED;
                    entry.error = "no texture manager";
                }
                return;
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(finalImage);
            tm.registerTexture(entry.id, tex);
            tex.upload();
            // same no-mipmap trap as the 1x1 defaults, linear here for smoothing on uploaded assets
            tex.setFilter(true, false);
            synchronized (LOCK) {
                entry.state = TextureState.READY;
                entry.error = "";
                entry.size = new TextureSize(finalImage.getWidth(), finalImage.getHeight());
            }
        });
    }

    private enum TextureState {
        NEW,
        REQUESTED,
        READY,
        FAILED
    }

    private static final class TextureEntry {
        private final AssetHash hash;
        private final Identifier id;
        private TextureState state = TextureState.NEW;
        private String error = "";
        private TextureSize size = DEFAULT_SIZE;

        private TextureEntry(AssetHash hash, Identifier id) {
            this.hash = Objects.requireNonNull(hash, "hash");
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return "TextureEntry{hash=" + hash.hex().substring(0, 8) + ", state=" + state.name().toLowerCase(Locale.ROOT) + "}";
        }
    }

    public record TextureSize(int width, int height) {}
}
