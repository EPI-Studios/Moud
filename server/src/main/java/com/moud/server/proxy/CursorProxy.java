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
    private final CursorService cursorService;

    public CursorProxy(Player player) {
        this.player = player;
        this.cursorService = CursorService.getInstance();
    }

    private Cursor getCursor() {
        Cursor cursor = cursorService.getCursor(player);
        if (cursor == null) {
            throw new IllegalStateException("Cursor not found for player: " + player.getUsername() + ". Make sure the player is properly connected and the cursor service is initialized.");
        }
        return cursor;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return getCursor().getWorldPosition();
    }

    @HostAccess.Export
    public Vector3 getNormal() {
        return getCursor().getWorldNormal();
    }

    @HostAccess.Export
    public boolean isHittingBlock() {
        return getCursor().isHittingBlock();
    }

    @HostAccess.Export
    public void setMode(String mode) {
        try {
            getCursor().setMode(Cursor.CursorMode.valueOf(mode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor mode. Use 'THREE_DIMENSIONAL' or 'TWO_DIMENSIONAL'.");
        }
    }

    @HostAccess.Export
    public void setVisible(boolean visible) {
        Cursor cursor = getCursor();
        if (cursor.isGloballyVisible() != visible) {
            cursor.setGloballyVisible(visible);
            cursorService.sendVisibilityUpdate(cursor);
        }
    }

    @HostAccess.Export
    public void setTexture(String texturePath) {
        Cursor cursor = getCursor();
        cursor.setTexture(texturePath);
        cursor.setRenderMode(Cursor.RenderMode.TEXTURE);
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setColor(double r, double g, double b) {
        Cursor cursor = getCursor();
        cursor.setColor(new Vector3((float) r, (float) g, (float) b));
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setScale(double scale) {
        Cursor cursor = getCursor();
        cursor.setScale((float) scale);
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public void setVisibleTo(Value players) {
        Cursor cursor = getCursor();
        cursor.setVisibilityList(getPlayerUuids(players), true);
        cursorService.sendVisibilityUpdate(cursor);
    }

    @HostAccess.Export
    public void hideFrom(Value players) {
        Cursor cursor = getCursor();
        cursor.setVisibilityList(getPlayerUuids(players), false);
        cursorService.sendVisibilityUpdate(cursor);
    }

    @HostAccess.Export
    public void setVisibleToAll() {
        Cursor cursor = getCursor();
        cursor.setVisibilityList(new HashSet<>(), true);
        cursorService.sendVisibilityUpdate(cursor);
    }

    @HostAccess.Export
    public void projectOntoBlock(boolean enabled) {
        Cursor cursor = getCursor();
        cursor.setProjectOntoBlock(enabled);
        cursorService.sendAppearanceUpdate(cursor);
    }

    @HostAccess.Export
    public float getScale() {
        return getCursor().getScale();
    }

    @HostAccess.Export
    public String getTexture() {
        return getCursor().getTexture();
    }

    @HostAccess.Export
    public Vector3 getColor() {
        return getCursor().getColor();
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