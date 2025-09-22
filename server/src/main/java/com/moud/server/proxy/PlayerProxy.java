package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerPacketWrapper;
import com.moud.server.player.PlayerCameraManager;
import com.moud.server.shared.api.SharedValueApiProxy;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerProxy.class);
    private final Player player;
    private final ClientProxy client;
    private final APIValidator validator;
    private final SharedValueApiProxy sharedValues;
    private final CameraLockProxy camera;
    private final PlayerUIProxy ui;
    private final CursorProxy cursor;

    public PlayerProxy(Player player) {
        this.player = player;
        this.client = new ClientProxy(player);
        this.validator = new APIValidator();
        this.sharedValues = new SharedValueApiProxy(player);
        this.camera = new CameraLockProxy(player);
        this.ui = new PlayerUIProxy(player);
        this.cursor = new CursorProxy(player);
    }

    @HostAccess.Export
    public CameraLockProxy getCamera() {

        LOGGER.debug("Script accessed getCamera() for player '{}'", player.getUsername());
        return camera;
    }

    @HostAccess.Export
    public String getName() {
        return player.getUsername();
    }

    @HostAccess.Export
    public String getUuid() {
        return player.getUuid().toString();
    }

    @HostAccess.Export
    public void sendMessage(String message) {
        validator.validateString(message, "message");
        player.sendMessage(message);
    }

    @HostAccess.Export
    public void kick(String reason) {
        validator.validateString(reason, "reason");
        player.kick(reason);
    }

    @HostAccess.Export
    public boolean isOnline() {
        return player.isOnline();
    }

    @HostAccess.Export
    public ClientProxy getClient() {
        return client;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        Pos pos = player.getPosition();
        return new Vector3((float)pos.x(), (float)pos.y(), (float)pos.z());
    }

    @HostAccess.Export
    public Vector3 getDirection() {
        Vec dir = player.getPosition().direction();
        return new Vector3((float)dir.x(), (float)dir.y(), (float)dir.z());
    }

    @HostAccess.Export
    public Vector3 getCameraDirection() {
        return PlayerCameraManager.getInstance().getCameraDirection(player);
    }

    @HostAccess.Export
    public Vector3 getHeadRotation() {
        Pos pos = player.getPosition();
        return new Vector3(pos.yaw(), pos.pitch(), 0.0f);
    }

    @HostAccess.Export
    public float getYaw() {
        return player.getPosition().yaw();
    }

    @HostAccess.Export
    public float getPitch() {
        return player.getPosition().pitch();
    }

    @HostAccess.Export
    public void teleport(double x, double y, double z) {
        validator.validateCoordinates(x, y, z);
        player.teleport(new Pos(x, y, z));
    }

    @HostAccess.Export
    public SharedValueApiProxy getShared() {
        return sharedValues;
    }

    @HostAccess.Export
    public PlayerUIProxy getUi() {
        return ui;
    }

    @HostAccess.Export
    public CursorProxy getCursor() {
        return cursor;
    }

    @HostAccess.Export
    public void setVanished(boolean vanished) {
        if (vanished) {
            player.setInvisible(true);
        } else {
            player.setInvisible(false);
        }
    }

    @HostAccess.Export
    public void playAnimation(String animationId) {
        if (player.isOnline()) {
            player.sendPacket(ServerPacketWrapper.createPacket(new MoudPackets.S2C_PlayPlayerAnimationPacket(animationId)));
        }
    }
}