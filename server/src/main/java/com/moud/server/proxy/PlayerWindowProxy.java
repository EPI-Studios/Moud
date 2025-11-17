package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TsExpose
public class PlayerWindowProxy {
    private final Player player;
    private final ServerNetworkManager networkManager;

    public PlayerWindowProxy(Player player) {
        this.player = player;
        this.networkManager = ServerNetworkManager.getInstance();
    }

    private void send(MoudPackets.S2C_ManageWindowPacket.Action action, int int1, int int2, String string1, boolean bool1) {
        if (player.isOnline()) {
            networkManager.send(player, new MoudPackets.S2C_ManageWindowPacket(action, int1, int2, string1, bool1));
        }
    }

    @HostAccess.Export
    @Deprecated
    public void setSize(int width, int height) {
        send(MoudPackets.S2C_ManageWindowPacket.Action.SET_SIZE, width, height, "", false);
    }

    @HostAccess.Export
    @Deprecated
    public void setPosition(int x, int y) {
        send(MoudPackets.S2C_ManageWindowPacket.Action.SET_POSITION, x, y, "", false);
    }

    @HostAccess.Export
    public void transitionTo(Value options) {
        if (!player.isOnline() || options == null || !options.hasMembers()) {
            return;
        }

        int targetX = options.hasMember("x") ? options.getMember("x").asInt() : -1;
        int targetY = options.hasMember("y") ? options.getMember("y").asInt() : -1;
        int targetWidth = options.hasMember("width") ? options.getMember("width").asInt() : -1;
        int targetHeight = options.hasMember("height") ? options.getMember("height").asInt() : -1;
        int duration = options.hasMember("duration") ? options.getMember("duration").asInt() : 500;
        String easing = options.hasMember("easing") ? options.getMember("easing").asString() : "ease-out-quad";

        MoudPackets.S2C_TransitionWindowPacket packet = new MoudPackets.S2C_TransitionWindowPacket(
                targetX, targetY, targetWidth, targetHeight, duration, easing
        );
        networkManager.send(player, packet);
    }

    @HostAccess.Export
    public void playSequence(Value sequence) {
        if (!player.isOnline() || sequence == null || !sequence.hasArrayElements()) {
            return;
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < sequence.getArraySize(); i++) {
            Value stepValue = sequence.getArrayElement(i);
            if (stepValue.hasMembers()) {
                Map<String, Object> stepMap = new HashMap<>();
                for (String key : stepValue.getMemberKeys()) {
                    Value member = stepValue.getMember(key);
                    if (member.isNumber()) stepMap.put(key, member.asDouble());
                    else if (member.isString()) stepMap.put(key, member.asString());
                    else if (member.isBoolean()) stepMap.put(key, member.asBoolean());
                }
                steps.add(stepMap);
            }
        }

        networkManager.send(player, new MoudPackets.S2C_WindowSequencePacket(steps));
    }

    @HostAccess.Export
    public void setTitle(String title) {
        send(MoudPackets.S2C_ManageWindowPacket.Action.SET_TITLE, 0, 0, title, false);
    }

    @HostAccess.Export
    public void setBorderless(boolean borderless) {
        send(MoudPackets.S2C_ManageWindowPacket.Action.SET_BORDERLESS, 0, 0, "", borderless);
    }

    @HostAccess.Export
    public void maximize() {
        send(MoudPackets.S2C_ManageWindowPacket.Action.MAXIMIZE, 0, 0, "", false);
    }

    @HostAccess.Export
    public void minimize() {
        send(MoudPackets.S2C_ManageWindowPacket.Action.MINIMIZE, 0, 0, "", false);
    }

    @HostAccess.Export
    public void restore() {
        send(MoudPackets.S2C_ManageWindowPacket.Action.RESTORE, 0, 0, "", false);
    }
}