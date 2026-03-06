package com.moud.net.wire.codec;

import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.net.wire.WireIo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ScriptCodec {
    private ScriptCodec() {
    }

    public static void writeScriptActionListRequest(ByteBuffer out, ScriptActionListRequest request) {
        WireIo.writeLong(out, request.requestId());
        WireIo.writeLong(out, request.nodeId());
    }

    public static ScriptActionListRequest readScriptActionListRequest(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        long nodeId = WireIo.readLong(in);
        return new ScriptActionListRequest(requestId, nodeId);
    }

    public static void writeScriptActionListResponse(ByteBuffer out, ScriptActionListResponse response) {
        WireIo.writeLong(out, response.requestId());
        WireIo.writeLong(out, response.nodeId());
        WireIo.writeVarInt(out, response.success() ? 1 : 0);
        WireIo.writeString(out, response.error());
        List<String> actions = response.actions();
        WireIo.writeVarInt(out, actions == null ? 0 : actions.size());
        if (actions != null) {
            for (String action : actions) {
                WireIo.writeString(out, action);
            }
        }
    }

    public static ScriptActionListResponse readScriptActionListResponse(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        long nodeId = WireIo.readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        int count = WireIo.readVarInt(in);
        ArrayList<String> actions = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            actions.add(WireIo.readString(in));
        }
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new ScriptActionListResponse(requestId, nodeId, success, error, List.copyOf(actions));
    }

    public static void writeScriptActionInvoke(ByteBuffer out, ScriptActionInvoke invoke) {
        WireIo.writeLong(out, invoke.requestId());
        WireIo.writeLong(out, invoke.nodeId());
        WireIo.writeString(out, invoke.action());
    }

    public static ScriptActionInvoke readScriptActionInvoke(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        long nodeId = WireIo.readLong(in);
        String action = WireIo.readString(in);
        return new ScriptActionInvoke(requestId, nodeId, action);
    }

    public static void writeScriptActionInvokeAck(ByteBuffer out, ScriptActionInvokeAck ack) {
        WireIo.writeLong(out, ack.requestId());
        WireIo.writeLong(out, ack.nodeId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
    }

    public static ScriptActionInvokeAck readScriptActionInvokeAck(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        long nodeId = WireIo.readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new ScriptActionInvokeAck(requestId, nodeId, success, error);
    }

    public static void writeScriptFileReadRequest(ByteBuffer out, ScriptFileReadRequest request) {
        WireIo.writeLong(out, request.requestId());
        WireIo.writeString(out, request.path());
    }

    public static ScriptFileReadRequest readScriptFileReadRequest(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        String path = WireIo.readString(in);
        return new ScriptFileReadRequest(requestId, path);
    }

    public static void writeScriptFileReadResponse(ByteBuffer out, ScriptFileReadResponse response) {
        WireIo.writeLong(out, response.requestId());
        WireIo.writeVarInt(out, response.success() ? 1 : 0);
        WireIo.writeString(out, response.path());
        WireIo.writeString(out, response.content());
        WireIo.writeString(out, response.error());
    }

    public static ScriptFileReadResponse readScriptFileReadResponse(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String path = WireIo.readString(in);
        String content = WireIo.readString(in);
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new ScriptFileReadResponse(requestId, success, path, content, error);
    }

    public static void writeScriptFileWriteRequest(ByteBuffer out, ScriptFileWriteRequest request) {
        WireIo.writeLong(out, request.requestId());
        WireIo.writeString(out, request.path());
        WireIo.writeString(out, request.content());
    }

    public static ScriptFileWriteRequest readScriptFileWriteRequest(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        String path = WireIo.readString(in);
        String content = WireIo.readString(in);
        return new ScriptFileWriteRequest(requestId, path, content);
    }

    public static void writeScriptFileWriteAck(ByteBuffer out, ScriptFileWriteAck ack) {
        WireIo.writeLong(out, ack.requestId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.path());
        WireIo.writeString(out, ack.error());
    }

    public static ScriptFileWriteAck readScriptFileWriteAck(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String path = WireIo.readString(in);
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new ScriptFileWriteAck(requestId, success, path, error);
    }

    // --- Size estimations ---

    public static int scriptFileReadRequestSize(ScriptFileReadRequest request) {
        return WireIo.longSize(request.requestId()) + WireIo.stringSize(request.path());
    }

    public static int scriptFileReadResponseSize(ScriptFileReadResponse response) {
        return WireIo.longSize(response.requestId())
                + WireIo.varIntSize(response.success() ? 1 : 0)
                + WireIo.stringSize(response.path())
                + WireIo.stringSize(response.content())
                + WireIo.stringSize(response.error());
    }

    public static int scriptFileWriteRequestSize(ScriptFileWriteRequest request) {
        return WireIo.longSize(request.requestId())
                + WireIo.stringSize(request.path())
                + WireIo.stringSize(request.content());
    }

    public static int scriptFileWriteAckSize(ScriptFileWriteAck ack) {
        return WireIo.longSize(ack.requestId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.path())
                + WireIo.stringSize(ack.error());
    }

    public static int scriptActionListRequestSize(ScriptActionListRequest request) {
        return WireIo.longSize(request.requestId()) + WireIo.longSize(request.nodeId());
    }

    public static int scriptActionListResponseSize(ScriptActionListResponse response) {
        int size = WireIo.longSize(response.requestId())
                + WireIo.longSize(response.nodeId())
                + WireIo.varIntSize(response.success() ? 1 : 0)
                + WireIo.stringSize(response.error());
        List<String> actions = response.actions();
        size += WireIo.varIntSize(actions == null ? 0 : actions.size());
        if (actions != null) {
            for (String action : actions) {
                size += WireIo.stringSize(action);
            }
        }
        return size;
    }

    public static int scriptActionInvokeSize(ScriptActionInvoke invoke) {
        return WireIo.longSize(invoke.requestId())
                + WireIo.longSize(invoke.nodeId())
                + WireIo.stringSize(invoke.action());
    }

    public static int scriptActionInvokeAckSize(ScriptActionInvokeAck ack) {
        return WireIo.longSize(ack.requestId())
                + WireIo.longSize(ack.nodeId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.error());
    }
}
