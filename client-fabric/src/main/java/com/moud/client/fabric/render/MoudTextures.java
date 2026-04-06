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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

public final class MoudTextures implements AssetsClient.Listener {
    public static final Identifier WHITE_ID = Identifier.of("moud", "dynamic/white");
    public static final Identifier BLACK_ID = Identifier.of("moud", "dynamic/black");
    public static final Identifier FLAT_NORMAL_ID = Identifier.of("moud", "dynamic/flat_normal");
    public static final Identifier ORM_DEFAULT_ID = Identifier.of("moud", "dynamic/orm_default");

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
            if (defaultsRegistered) {
                try {
                    tm.destroyTexture(WHITE_ID);
                } catch (Exception ignored) {
                }
                try {
                    tm.destroyTexture(BLACK_ID);
                } catch (Exception ignored) {
                }
                try {
                    tm.destroyTexture(FLAT_NORMAL_ID);
                } catch (Exception ignored) {
                }
                try {
                    tm.destroyTexture(ORM_DEFAULT_ID);
                } catch (Exception ignored) {
                }
                defaultsRegistered = false;
            }
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
            maybeRequestManifest();
            return TextureManager.MISSING_IDENTIFIER;
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
        synchronized (LOCK) {
            if (defaultsRegistered) {
                return;
            }
            defaultsRegistered = true;
        }

        Runnable register = () -> {
            registerSolidTexture(tm, WHITE_ID, 0xFFFFFFFF);
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

    private static void registerSolidTexture(TextureManager tm, Identifier id, int color) {
        NativeImageBackedTexture tex = new NativeImageBackedTexture(1, 1, false);
        NativeImage img = tex.getImage();
        if (img != null) {
            img.setColor(0, 0, color);
        }
        tm.registerTexture(id, tex);
        tex.upload();
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
        TextureEntry entry;
        synchronized (LOCK) {
            entry = texturesByHash.get(hash);
            if (entry == null) {
                entry = new TextureEntry(hash, Identifier.of("moud", "asset/" + hash.hex()));
                texturesByHash.put(hash, entry);
            }
        }

        if (status != AssetTransferStatus.OK || bytes == null || bytes.length == 0) {
            synchronized (LOCK) {
                entry.state = TextureState.FAILED;
                entry.error = message == null ? "" : message;
            }
            return;
        }

        TextureEntry finalEntry = entry;
        Thread.ofVirtual().name("moud-tex-decode").start(() -> decodeAndUpload(finalEntry, bytes));
    }

    private static void decodeAndUpload(TextureEntry entry, byte[] bytes) {
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
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
            synchronized (LOCK) {
                entry.state = TextureState.READY;
                entry.error = "";
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

        private TextureEntry(AssetHash hash, Identifier id) {
            this.hash = Objects.requireNonNull(hash, "hash");
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return "TextureEntry{hash=" + hash.hex().substring(0, 8) + ", state=" + state.name().toLowerCase(Locale.ROOT) + "}";
        }
    }
}
