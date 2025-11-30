package com.moud.server.api;

import com.moud.server.camera.CameraRegistry;
import com.moud.server.camera.CameraService;
import com.moud.server.camera.SceneCamera;
import com.moud.server.proxy.PlayerProxy;
import com.moud.server.ts.TsExpose;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;

@TsExpose
public class CameraAPI {
    @HostAccess.Export
    public SceneCamera getById(String id) {
        return CameraRegistry.getInstance().getById(id);
    }

    @HostAccess.Export
    public SceneCamera getByLabel(String label) {
        return CameraRegistry.getInstance().getByLabel(label);
    }

    @HostAccess.Export
    public boolean setPlayerCamera(Object player, String cameraIdOrLabel) {
        Player target = unwrap(player);
        if (target == null) {
            return false;
        }
        return CameraService.getInstance().setPlayerCamera(target, cameraIdOrLabel);
    }

    @HostAccess.Export
    public void clearPlayerCamera(Object player) {
        Player target = unwrap(player);
        if (target != null) {
            CameraService.getInstance().clearPlayerCamera(target);
        }
    }

    private Player unwrap(Object obj) {
        if (obj instanceof PlayerProxy proxy) {
            try {
                var field = PlayerProxy.class.getDeclaredField("player");
                field.setAccessible(true);
                Object p = field.get(proxy);
                if (p instanceof Player player) {
                    return player;
                }
            } catch (Exception ignored) {
            }
        } else if (obj instanceof Player p) {
            return p;
        }
        return null;
    }
}
