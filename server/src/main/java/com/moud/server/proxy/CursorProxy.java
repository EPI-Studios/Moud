package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.server.cursor.Cursor;
import com.moud.server.cursor.CursorService;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CursorProxy {
    private final Player player;
    private final Cursor cursor;
    private final CursorService cursorService;

    public CursorProxy(Player player) {
        this.player = player;
        this.cursorService = CursorService.getInstance();
        this.cursor = cursorService.getCursor(player);
        if (this.cursor == null) {
            throw new IllegalStateException("Cursor not found for player: " + player.getUsername());
        }
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return cursor.getWorldPosition();
    }

    @HostAccess.Export
    public Vector3 getNormal() {
        return cursor.getWorldNormal();
    }

    @HostAccess.Export
    public boolean isHittingBlock() {
        return cursor.isHittingBlock();
    }

    @HostAccess.Export
    public void setMode(String mode) {
        try {
            cursor.setMode(Cursor.CursorMode.valueOf(mode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor mode. Use 'THREE_DIMENSIONAL' or 'TWO_DIMENSIONAL'.");
        }
    }

    @HostAccess.Export
    public void setVisible(boolean visible) {
        if (cursor.isGloballyVisible() != visible) {
            cursor.setGloballyVisible(visible);
            cursorService.sendVisibilityUpdate(cursor);
        }
    }

    @HostAccess.Export
    public void setTexture(String texturePath) {
        cursor.setTexture(texturePath);
        cursor.setRenderMode(Cursor.RenderMode.TEXTURE);
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setColor(float r, float g, float b) {
        cursor.setColor(new Vector3(r, g, b));
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setScale(float scale) {
        cursor.setScale(scale);
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setVisibleTo(Value players) {
        cursor.setVisibilityList(getPlayerUuids(players), true);
        cursorService.sendVisibilityUpdate(cursor);
    }

    @HostAccess.Export
    public void hideFrom(Value players) {
        cursor.setVisibilityList(getPlayerUuids(players), false);
        cursorService.sendVisibilityUpdate(cursor);
    }

    @HostAccess.Export
    public void setVisibleToAll() {
        cursor.setVisibilityList(new HashSet<>(), true);
        cursorService.sendVisibilityUpdate(cursor);
    }

    private Set<UUID> getPlayerUuids(Value players) {
        if (players.isHostObject() && players.asHostObject() instanceof PlayerProxy) {
            return Set.of(UUID.fromString(((PlayerProxy) players.asHostObject()).getUuid()));
        }

        if (players.hasArrayElements()) {
            Set<UUID> uuids = new HashSet<>();
            for (int i = 0; i < players.getArraySize(); i++) {
                Value playerValue = players.getArrayElement(i);
                if (playerValue.isHostObject() && playerValue.asHostObject() instanceof PlayerProxy) {
                    uuids.add(UUID.fromString(((PlayerProxy) playerValue.asHostObject()).getUuid()));
                }
            }
            return uuids;
        }
        return new HashSet<>();
    }
}