package com.meekdev.moud.mod.adapter.image;

import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.core.image.GlyphFont;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.place.PlaceToml;
import com.mojang.blaze3d.opengl.GlTexture;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ClientImageSources extends ImageSources {

    public static final ClientImageSources INSTANCE = new ClientImageSources();

    private static final long SKIN_WAIT_NANOS = 2_000_000_000L;

    private record Asked(UUID player, boolean head, long since, CompletableFuture<EditableImage> result) {}

    private final List<Asked> waiting = new ArrayList<>();
    private GlyphFont font;
    private List<PackResources> fontPacks = List.of();

    private ClientImageSources() {
        super(ClientImageSources::root);
    }

    private static Path root() {
        Path root = ClientPlace.root();
        return root == null ? PlaceToml.root() : root;
    }

    @Override
    protected CompletableFuture<EditableImage> other(String source) {
        Identifier id = Identifier.tryParse(source);
        if (id == null) return CompletableFuture.failedFuture(new IllegalArgumentException(source + " is not a res:// file, a web address or a texture id"));
        String path = id.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("there is no texture " + file));
        try (InputStream in = resource.get().open()) {
            return CompletableFuture.completedFuture(decode(in.readAllBytes()));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new UncheckedIOException(e));
        }
    }

    @Override
    public synchronized GlyphFont font() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        List<PackResources> packs = resources.listPacks().toList();
        if (font == null || !packs.equals(fontPacks)) {
            font = MinecraftFont.read(resources);
            fontPacks = packs;
        }
        return font;
    }

    @Override
    public CompletableFuture<EditableImage> skin(String player, boolean head) {
        UUID id;
        try {
            id = UUID.fromString(player);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(player + " is not a player"));
        }
        CompletableFuture<EditableImage> result = new CompletableFuture<>();
        waiting.add(new Asked(id, head, System.nanoTime(), result));
        return result;
    }

    public void frame() {
        if (waiting.isEmpty()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        for (Iterator<Asked> it = waiting.iterator(); it.hasNext(); ) {
            Asked asked = it.next();
            if (asked.result().isDone()) {
                it.remove();
                continue;
            }
            boolean late = System.nanoTime() - asked.since() > SKIN_WAIT_NANOS;
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(asked.player());
            if (info == null) {
                if (late) asked.result().completeExceptionally(new IllegalStateException("that player is not in the game"));
                if (late) it.remove();
                continue;
            }
            Identifier sheet = info.getSkin().body().texturePath();
            boolean fallback = sheet.equals(DefaultPlayerSkin.get(asked.player()).body().texturePath());
            if (fallback && !late) continue;
            EditableImage skin = read(sheet);
            if (skin == null && !late) continue;
            if (skin == null) asked.result().completeExceptionally(new IllegalStateException("the skin never finished loading"));
            else asked.result().complete(asked.head() ? head(skin) : skin);
            it.remove();
        }
    }

    private static EditableImage read(Identifier texturePath) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(texturePath);
        if (texture == null || !(texture.getTexture() instanceof GlTexture gl)) return null;
        Textures.Pixels pixels = Textures.read(gl.glId(), EditableImage.LARGEST);
        return pixels == null ? null : EditableImage.of(pixels.width(), pixels.height(), pixels.argb());
    }
}
