package com.moud.client.fabric.render;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

public final class MoudTextures implements AssetsClient.Listener {
    public static final Identifier WHITE_ID = Identifier.of("moud", "dynamic/white");

    private static final Object LOCK = new Object();
    private static MoudTextures instance;

    private static AssetsClient assets;
    private static boolean whiteRegistered;

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
        ensureWhiteRegistered();
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
            if (whiteRegistered) {
                try {
                    tm.destroyTexture(WHITE_ID);
                } catch (Exception ignored) {
                }
                whiteRegistered = false;
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
        }
    }

    public static List<String> imageAssetPaths() {
        synchronized (LOCK) {
            return imagePaths;
        }
    }

    public static Identifier resolve(String textureRef) {
        ensureWhiteRegistered();
        if (textureRef == null || textureRef.isBlank()) {
            return WHITE_ID;
        }
        String ref = textureRef.trim();
        if (ref.startsWith(ResPath.SCHEME)) {
            return resolveResTexture(ref);
        }
        Identifier id = Identifier.tryParse(ref);
        return id != null ? id : TextureManager.MISSING_IDENTIFIER;
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

    private static void ensureWhiteRegistered() {
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager tm = client == null ? null : client.getTextureManager();
        if (tm == null) {
            return;
        }
        synchronized (LOCK) {
            if (whiteRegistered) {
                return;
            }
            whiteRegistered = true;
        }

        Runnable register = () -> {
            NativeImageBackedTexture tex = new NativeImageBackedTexture(1, 1, false);
            NativeImage img = tex.getImage();
            if (img != null) {
                img.setColor(0, 0, 0xFFFFFFFF);
            }
            tm.registerTexture(WHITE_ID, tex);
            tex.upload();
        };
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(register::run);
        } else {
            register.run();
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

        NativeImage image;
        try {
            image = NativeImage.read(bytes);
        } catch (Exception e) {
            synchronized (LOCK) {
                entry.state = TextureState.FAILED;
                entry.error = e.getMessage() == null ? "decode failed" : e.getMessage();
            }
            return;
        }

        TextureEntry finalEntry = entry;
        Runnable register = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager tm = client == null ? null : client.getTextureManager();
            if (tm == null) {
                image.close();
                synchronized (LOCK) {
                    finalEntry.state = TextureState.FAILED;
                    finalEntry.error = "no texture manager";
                }
                return;
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
            tm.registerTexture(finalEntry.id, tex);
            tex.upload();
            synchronized (LOCK) {
                finalEntry.state = TextureState.READY;
                finalEntry.error = "";
            }
        };

        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(register::run);
        } else {
            register.run();
        }
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
