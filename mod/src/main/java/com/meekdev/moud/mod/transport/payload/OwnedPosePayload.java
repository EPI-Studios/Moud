package com.meekdev.moud.mod.transport.payload;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OwnedPosePayload(List<Pose> poses) implements CustomPacketPayload {

    public static final int MOST = 256;

    public record Pose(int id, double x, double y, double z, float qx, float qy, float qz, float qw,
                       float vx, float vy, float vz, float wx, float wy, float wz) {}

    public static final Type<OwnedPosePayload> TYPE = Payloads.type("owned_pose");

    public static final StreamCodec<FriendlyByteBuf, OwnedPosePayload> CODEC = CustomPacketPayload.codec(
            (m, out) -> {
                out.writeVarInt(m.poses().size());
                for (Pose p : m.poses()) {
                    out.writeVarInt(p.id());
                    out.writeDouble(p.x());
                    out.writeDouble(p.y());
                    out.writeDouble(p.z());
                    out.writeFloat(p.qx());
                    out.writeFloat(p.qy());
                    out.writeFloat(p.qz());
                    out.writeFloat(p.qw());
                    out.writeFloat(p.vx());
                    out.writeFloat(p.vy());
                    out.writeFloat(p.vz());
                    out.writeFloat(p.wx());
                    out.writeFloat(p.wy());
                    out.writeFloat(p.wz());
                }
            },
            in -> {
                int count = in.readVarInt();
                if (count < 0 || count > MOST) throw new IllegalArgumentException("too many owned poses: " + count);
                List<Pose> poses = new ArrayList<>(count);
                for (int n = 0; n < count; n++) {
                    poses.add(new Pose(in.readVarInt(), in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                            in.readFloat(), in.readFloat(), in.readFloat(),
                            in.readFloat(), in.readFloat(), in.readFloat()));
                }
                return new OwnedPosePayload(poses);
            });

    @Override
    public Type<OwnedPosePayload> type() {
        return TYPE;
    }
}
