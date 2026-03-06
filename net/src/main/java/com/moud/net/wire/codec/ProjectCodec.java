package com.moud.net.wire.codec;

import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.wire.WireIo;

import java.nio.ByteBuffer;

public final class ProjectCodec {
    private ProjectCodec() {
    }

    public static void writeProjectInfo(ByteBuffer out, ProjectInfo info) {
        WireIo.writeLong(out, info.requestId());
        WireIo.writeVarInt(out, info.exists() ? 1 : 0);
        WireIo.writeString(out, info.name());
        WireIo.writeString(out, info.author());
    }

    public static ProjectInfo readProjectInfo(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        boolean exists = WireIo.readVarInt(in) != 0;
        String name = WireIo.readString(in);
        String author = WireIo.readString(in);
        return new ProjectInfo(requestId, exists, name, author);
    }

    public static void writeProjectCreate(ByteBuffer out, ProjectCreate create) {
        WireIo.writeLong(out, create.requestId());
        WireIo.writeString(out, create.name());
        WireIo.writeString(out, create.author());
    }

    public static ProjectCreate readProjectCreate(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        String name = WireIo.readString(in);
        String author = WireIo.readString(in);
        return new ProjectCreate(requestId, name, author);
    }

    public static void writeProjectCreateAck(ByteBuffer out, ProjectCreateAck ack) {
        WireIo.writeLong(out, ack.requestId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
        WireIo.writeString(out, ack.name());
        WireIo.writeString(out, ack.author());
    }

    public static ProjectCreateAck readProjectCreateAck(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        String name = WireIo.readString(in);
        String author = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new ProjectCreateAck(requestId, success, error, name, author);
    }

    // --- Size estimations ---

    public static int projectInfoSize(ProjectInfo info) {
        return WireIo.longSize(info.requestId())
                + WireIo.varIntSize(info.exists() ? 1 : 0)
                + WireIo.stringSize(info.name())
                + WireIo.stringSize(info.author());
    }

    public static int projectCreateSize(ProjectCreate create) {
        return WireIo.longSize(create.requestId())
                + WireIo.stringSize(create.name())
                + WireIo.stringSize(create.author());
    }

    public static int projectCreateAckSize(ProjectCreateAck ack) {
        return WireIo.longSize(ack.requestId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.error())
                + WireIo.stringSize(ack.name())
                + WireIo.stringSize(ack.author());
    }
}
