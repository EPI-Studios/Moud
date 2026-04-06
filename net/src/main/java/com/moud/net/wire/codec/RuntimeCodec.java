package com.moud.net.wire.codec;

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
        out.putFloat(input.cursorX());
        out.putFloat(input.cursorY());
        int flags = 0;
        if (input.jump()) flags |= 1;
        if (input.sprint()) flags |= 2;
        WireIo.writeVarInt(out, flags);
    }

    public static PlayerInput readPlayerInput(ByteBuffer in) {
        long tick = WireIo.readLong(in);
        float moveX = in.getFloat();
        float moveZ = in.getFloat();
        float yaw = in.getFloat();
        float pitch = in.getFloat();
        float cursorX = in.getFloat();
        float cursorY = in.getFloat();
        int flags = WireIo.readVarInt(in);
        return new PlayerInput(tick, moveX, moveZ, yaw, pitch, cursorX, cursorY, (flags & 1) != 0, (flags & 2) != 0);
    }

    public static void writeRuntimeState(ByteBuffer out, RuntimeState state) {
        WireIo.writeLong(out, state.serverTick());
        WireIo.writeString(out, state.sceneId());
        WireIo.writeVarInt(out, state.fogEnabled() ? 1 : 0);
        out.putFloat(state.fogColorR());
        out.putFloat(state.fogColorG());
        out.putFloat(state.fogColorB());
        out.putFloat(state.fogDensity());
        WireIo.writeVarInt(out, state.timeTicks());
        WireIo.writeString(out, state.weather());
        out.putFloat(state.ambientLight());
        WireIo.writeVarInt(out, state.useSceneCamera() ? 1 : 0);
        out.putFloat(state.sceneCamX());
        out.putFloat(state.sceneCamY());
        out.putFloat(state.sceneCamZ());
        out.putFloat(state.sceneCamYawDeg());
        out.putFloat(state.sceneCamPitchDeg());
        out.putFloat(state.sceneCamRollDeg());
        WireIo.writeVarInt(out, state.useFollowCamera() ? 1 : 0);
        out.putFloat(state.followCamLocalX());
        out.putFloat(state.followCamLocalY());
        out.putFloat(state.followCamLocalZ());
        out.putFloat(state.followCamPitchDeg());
        out.putFloat(state.followCamRollDeg());
        WireIo.writeVarInt(out, state.useScriptCamera() ? 1 : 0);
        out.putFloat(state.scriptCamX());
        out.putFloat(state.scriptCamY());
        out.putFloat(state.scriptCamZ());
        out.putFloat(state.scriptCamYawDeg());
        out.putFloat(state.scriptCamPitchDeg());
        out.putFloat(state.scriptCamRollDeg());
    }

    public static RuntimeState readRuntimeState(ByteBuffer in) {
        long tick = WireIo.readLong(in);
        String sceneId = WireIo.readString(in);
        boolean fogEnabled = WireIo.readVarInt(in) != 0;
        float fogColorR = in.getFloat();
        float fogColorG = in.getFloat();
        float fogColorB = in.getFloat();
        float fogDensity = in.getFloat();
        int timeTicks = WireIo.readVarInt(in);
        String weather = WireIo.readString(in);
        float ambientLight = in.getFloat();
        boolean useSceneCamera = WireIo.readVarInt(in) != 0;
        float sceneCamX = in.getFloat();
        float sceneCamY = in.getFloat();
        float sceneCamZ = in.getFloat();
        float sceneCamYawDeg = in.getFloat();
        float sceneCamPitchDeg = in.getFloat();
        float sceneCamRollDeg = in.getFloat();
        boolean useFollowCamera = WireIo.readVarInt(in) != 0;
        float followCamLocalX = in.getFloat();
        float followCamLocalY = in.getFloat();
        float followCamLocalZ = in.getFloat();
        float followCamPitchDeg = in.getFloat();
        float followCamRollDeg = in.getFloat();
        boolean useScriptCamera = WireIo.readVarInt(in) != 0;
        float scriptCamX = in.getFloat();
        float scriptCamY = in.getFloat();
        float scriptCamZ = in.getFloat();
        float scriptCamYawDeg = in.getFloat();
        float scriptCamPitchDeg = in.getFloat();
        float scriptCamRollDeg = in.getFloat();
        return new RuntimeState(tick, sceneId,
                fogEnabled, fogColorR, fogColorG, fogColorB, fogDensity,
                timeTicks, weather, ambientLight,
                useSceneCamera, sceneCamX, sceneCamY, sceneCamZ,
                sceneCamYawDeg, sceneCamPitchDeg, sceneCamRollDeg,
                useFollowCamera, followCamLocalX, followCamLocalY, followCamLocalZ,
                followCamPitchDeg, followCamRollDeg,
                useScriptCamera, scriptCamX, scriptCamY, scriptCamZ,
                scriptCamYawDeg, scriptCamPitchDeg, scriptCamRollDeg);
    }

    public static int playerInputSize(PlayerInput input) {
        return WireIo.longSize(input.clientTick()) + 6 * 4 + WireIo.varIntSize(0);
    }

    public static int runtimeStateSize(RuntimeState state) {
        int size = WireIo.longSize(state.serverTick());
        size += WireIo.stringSize(state.sceneId());
        size += WireIo.varIntSize(state.fogEnabled() ? 1 : 0);
        size += 3 * 4; // fogColorR/G/B
        size += 4;     // fogDensity
        size += WireIo.varIntSize(state.timeTicks());
        size += WireIo.stringSize(state.weather());
        size += 4;     // ambientLight
        size += WireIo.varIntSize(state.useSceneCamera() ? 1 : 0);
        size += 6 * 4; // sceneCam x/y/z/yaw/pitch/roll
        size += WireIo.varIntSize(state.useFollowCamera() ? 1 : 0);
        size += 5 * 4; // followCam localX/Y/Z/pitch/roll
        size += WireIo.varIntSize(state.useScriptCamera() ? 1 : 0);
        size += 6 * 4; // scriptCam x/y/z/yaw/pitch/roll
        return size;
    }
}
