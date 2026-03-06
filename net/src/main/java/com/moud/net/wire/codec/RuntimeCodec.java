package com.moud.net.wire.codec;

import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.wire.WireIo;

import java.nio.ByteBuffer;

public final class RuntimeCodec {
    private RuntimeCodec() {
    }

    public static void writePlayerInput(ByteBuffer out, PlayerInput input) {
        WireIo.writeLong(out, input.clientTick());
        out.putFloat(input.moveX());
        out.putFloat(input.moveZ());
        out.putFloat(input.yawDeg());
        out.putFloat(input.pitchDeg());
        int flags = 0;
        if (input.jump()) {
            flags |= 1;
        }
        if (input.sprint()) {
            flags |= 2;
        }
        WireIo.writeVarInt(out, flags);
    }

    public static PlayerInput readPlayerInput(ByteBuffer in) {
        long tick = WireIo.readLong(in);
        float moveX = in.getFloat();
        float moveZ = in.getFloat();
        float yaw = in.getFloat();
        float pitch = in.getFloat();
        int flags = WireIo.readVarInt(in);
        boolean jump = (flags & 1) != 0;
        boolean sprint = (flags & 2) != 0;
        return new PlayerInput(tick, moveX, moveZ, yaw, pitch, jump, sprint);
    }

    public static void writeRuntimeState(ByteBuffer out, RuntimeState state) {
        WireIo.writeLong(out, state.serverTick());
        WireIo.writeLong(out, state.lastProcessedTick());
        WireIo.writeString(out, state.sceneId());
        out.putFloat(state.charX());
        out.putFloat(state.charY());
        out.putFloat(state.charZ());
        out.putFloat(state.velX());
        out.putFloat(state.velY());
        out.putFloat(state.velZ());
        WireIo.writeVarInt(out, state.onFloor() ? 1 : 0);
        out.putFloat(state.camYawDeg());
        out.putFloat(state.camPitchDeg());
        WireIo.writeVarInt(out, state.fogEnabled() ? 1 : 0);
        WireIo.writeString(out, legacyFogColorString(state.fogColorR(), state.fogColorG(), state.fogColorB()));
        out.putFloat(state.fogDensity());
        out.putFloat(state.fogColorR());
        out.putFloat(state.fogColorG());
        out.putFloat(state.fogColorB());
        WireIo.writeVarInt(out, state.timeTicks());
        WireIo.writeString(out, state.weather());
        out.putFloat(state.ambientLight());
        WireIo.writeVarInt(out, state.hasCamera() ? 1 : 0);
        WireIo.writeVarInt(out, state.useSceneCamera() ? 1 : 0);
        out.putFloat(state.sceneCamX());
        out.putFloat(state.sceneCamY());
        out.putFloat(state.sceneCamZ());
        out.putFloat(state.sceneCamYawDeg());
        out.putFloat(state.sceneCamPitchDeg());
        out.putFloat(state.sceneCamRollDeg());
        WireIo.writeLong(out, state.bodyNodeId());
    }

    public static RuntimeState readRuntimeState(ByteBuffer in) {
        long tick = WireIo.readLong(in);
        long lastProcessedTick = WireIo.readLong(in);
        String sceneId = WireIo.readString(in);
        float charX = in.getFloat();
        float charY = in.getFloat();
        float charZ = in.getFloat();
        float velX = in.getFloat();
        float velY = in.getFloat();
        float velZ = in.getFloat();
        boolean onFloor = WireIo.readVarInt(in) != 0;
        float yaw = in.getFloat();
        float pitch = in.getFloat();
        boolean fogEnabled = WireIo.readVarInt(in) != 0;
        String fogColor = WireIo.readString(in); // legacy
        float fogDensity = in.getFloat();

        float fogColorR = 0.5f;
        float fogColorG = 0.5f;
        float fogColorB = 0.5f;
        if (fogColor != null && !fogColor.isBlank()) {
            int c1 = fogColor.indexOf(',');
            int c2 = c1 < 0 ? -1 : fogColor.indexOf(',', c1 + 1);
            if (c1 > 0 && c2 > c1) {
                fogColorR = ParseUtils.parseFloat(fogColor.substring(0, c1), fogColorR);
                fogColorG = ParseUtils.parseFloat(fogColor.substring(c1 + 1, c2), fogColorG);
                fogColorB = ParseUtils.parseFloat(fogColor.substring(c2 + 1), fogColorB);
            }
        }

        int timeTicks = 6000;
        String weather = "clear";
        float ambientLight = 1.0f;

        if (in.remaining() >= 3 * 4) {
            fogColorR = in.getFloat();
            fogColorG = in.getFloat();
            fogColorB = in.getFloat();
        }
        if (in.hasRemaining()) {
            timeTicks = WireIo.readVarInt(in);
        }
        if (in.hasRemaining()) {
            weather = WireIo.readString(in);
        }
        if (in.remaining() >= 4) {
            ambientLight = in.getFloat();
        }
        boolean hasCamera = true;
        if (in.hasRemaining()) {
            hasCamera = WireIo.readVarInt(in) != 0;
        }

        boolean useSceneCamera = false;
        float sceneCamX = 0.0f;
        float sceneCamY = 0.0f;
        float sceneCamZ = 0.0f;
        float sceneCamYawDeg = 0.0f;
        float sceneCamPitchDeg = 0.0f;

        if (in.hasRemaining()) {
            useSceneCamera = WireIo.readVarInt(in) != 0;
        }
        if (in.remaining() >= 5 * 4) {
            sceneCamX = in.getFloat();
            sceneCamY = in.getFloat();
            sceneCamZ = in.getFloat();
            sceneCamYawDeg = in.getFloat();
            sceneCamPitchDeg = in.getFloat();
        }
        float sceneCamRollDeg = 0.0f;
        if (in.remaining() >= 4) {
            sceneCamRollDeg = in.getFloat();
        }

        long bodyNodeId = 0L;
        if (in.hasRemaining()) {
            bodyNodeId = WireIo.readLong(in);
        }

        return new RuntimeState(tick, lastProcessedTick, sceneId,
                charX, charY, charZ, velX, velY, velZ, onFloor,
                yaw, pitch,
                fogEnabled, fogColorR, fogColorG, fogColorB,
                fogDensity, timeTicks, weather, ambientLight,
                hasCamera, useSceneCamera, sceneCamX, sceneCamY, sceneCamZ, sceneCamYawDeg, sceneCamPitchDeg, sceneCamRollDeg,
                bodyNodeId);
    }

    public static int playerInputSize(PlayerInput input) {
        int size = 0;
        size += WireIo.longSize(input.clientTick());
        size += 4 * 4;
        size += WireIo.varIntSize(0);
        return size;
    }

    public static int runtimeStateSize(RuntimeState state) {
        int size = 0;
        size += WireIo.longSize(state.serverTick());
        size += WireIo.longSize(state.lastProcessedTick());
        size += WireIo.stringSize(state.sceneId());
        size += 6 * 4; // charX/Y/Z + velX/Y/Z
        size += WireIo.varIntSize(state.onFloor() ? 1 : 0);
        size += 2 * 4; // camYawDeg + camPitchDeg
        size += WireIo.varIntSize(state.fogEnabled() ? 1 : 0);
        size += WireIo.stringSize(legacyFogColorString(state.fogColorR(), state.fogColorG(), state.fogColorB()));
        size += 4; // fogDensity
        size += 3 * 4; // fogColorR/G/B
        size += WireIo.varIntSize(state.timeTicks());
        size += WireIo.stringSize(state.weather());
        size += 4; // ambientLight
        size += WireIo.varIntSize(1); // hasCamera
        return size;
    }

    private static String legacyFogColorString(float r, float g, float b) {
        return Float.toString(r) + "," + Float.toString(g) + "," + Float.toString(b);
    }
}
