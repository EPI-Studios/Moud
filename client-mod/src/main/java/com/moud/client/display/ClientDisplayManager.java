package com.moud.client.display;

import com.moud.network.MoudPackets;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDisplayManager {
    private static final ClientDisplayManager INSTANCE = new ClientDisplayManager();

    private final Map<Long, DisplaySurface> displays = new ConcurrentHashMap<>();

    private ClientDisplayManager() {
    }

    public static ClientDisplayManager getInstance() {
        return INSTANCE;
    }

    public void handleCreate(MoudPackets.S2C_CreateDisplayPacket packet) {
        DisplaySurface surface = displays.compute(packet.displayId(), (id, existing) -> existing != null ? existing : new DisplaySurface(id));
        surface.applyCreatePacket(packet);
    }

    public void handleTransform(MoudPackets.S2C_UpdateDisplayTransformPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateTransform(packet.position(), packet.rotation(), packet.scale());
        }
    }

    public void handleAnchor(MoudPackets.S2C_UpdateDisplayAnchorPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateAnchor(packet.anchorType(), packet.anchorBlockPosition(), packet.anchorEntityUuid(), packet.anchorOffset());
        }
    }

    public void handleContent(MoudPackets.S2C_UpdateDisplayContentPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateContent(packet.contentType(), packet.primarySource(), packet.frameSources(), packet.frameRate(), packet.loop());
        }
    }

    public void handlePlayback(MoudPackets.S2C_UpdateDisplayPlaybackPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updatePlayback(packet.playing(), packet.playbackSpeed(), packet.startOffsetSeconds());
        }
    }

    public void remove(long id) {
        DisplaySurface surface = displays.remove(id);
        if (surface != null) {
            surface.dispose();
        }
    }

    public void clear() {
        displays.values().forEach(DisplaySurface::dispose);
        displays.clear();
        DisplayTextureResolver.getInstance().clear();
    }

    public Collection<DisplaySurface> getDisplays() {
        return displays.values();
    }

    public boolean isEmpty() {
        return displays.isEmpty();
    }
}