package com.moud.client.display;

import com.moud.client.editor.runtime.RuntimeObjectRegistry;
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
        RuntimeObjectRegistry.getInstance().syncDisplay(surface);
    }

    public void handleTransform(MoudPackets.S2C_UpdateDisplayTransformPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateTransform(packet.position(), packet.rotation(), packet.scale(), packet.billboardMode(), packet.renderThroughBlocks());
            RuntimeObjectRegistry.getInstance().syncDisplay(surface);
        }
    }

    public void handleAnchor(MoudPackets.S2C_UpdateDisplayAnchorPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateAnchor(packet.anchorType(),
                    packet.anchorBlockPosition(),
                    packet.anchorEntityUuid(),
                    packet.anchorOffset(),
                    packet.anchorModelId(),
                    packet.anchorOffsetLocal(),
                    packet.inheritRotation(),
                    packet.inheritScale(),
                    packet.includePitch());
            RuntimeObjectRegistry.getInstance().syncDisplay(surface);
        }
    }

    public void handleContent(MoudPackets.S2C_UpdateDisplayContentPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updateContent(packet.contentType(), packet.primarySource(), packet.frameSources(), packet.frameRate(), packet.loop());
            RuntimeObjectRegistry.getInstance().syncDisplay(surface);
        }
    }

    public void handlePlayback(MoudPackets.S2C_UpdateDisplayPlaybackPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updatePlayback(packet.playing(), packet.playbackSpeed(), packet.startOffsetSeconds());
            RuntimeObjectRegistry.getInstance().syncDisplay(surface);
        }
    }

    public void handlePbr(MoudPackets.S2C_UpdateDisplayPbrPacket packet) {
        DisplaySurface surface = displays.get(packet.displayId());
        if (surface != null) {
            surface.updatePbr(
                    packet.enabled(),
                    packet.baseColor(),
                    packet.normal(),
                    packet.metallicRoughness(),
                    packet.emissive(),
                    packet.occlusion(),
                    packet.metallicFactor(),
                    packet.roughnessFactor()
            );
            RuntimeObjectRegistry.getInstance().syncDisplay(surface);
        }
    }

    public void remove(long id) {
        DisplaySurface surface = displays.remove(id);
        if (surface != null) {
            surface.dispose();
            RuntimeObjectRegistry.getInstance().removeDisplay(id);
        }
    }

    public void clear() {
        displays.values().forEach(surface -> {
            surface.dispose();
            RuntimeObjectRegistry.getInstance().removeDisplay(surface.getId());
        });
        displays.clear();
        DisplayTextureResolver.getInstance().clear();
    }

    public void tickSmoothing(float deltaTicks) {
        displays.values().forEach(surface -> surface.tickSmoothing(deltaTicks));
    }

    public Collection<DisplaySurface> getDisplays() {
        return displays.values();
    }

    public DisplaySurface getById(long id) {
        return displays.get(id);
    }

    public boolean isEmpty() {
        return displays.isEmpty();
    }
}
