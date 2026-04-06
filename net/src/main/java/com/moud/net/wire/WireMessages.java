package com.moud.net.wire;

import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.Hello;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.MessageType;
import com.moud.net.protocol.Ping;
import com.moud.net.protocol.Pong;
import com.moud.net.protocol.AssetDownloadBegin;
import com.moud.net.protocol.AssetDownloadChunk;
import com.moud.net.protocol.AssetDownloadComplete;
import com.moud.net.protocol.AssetDownloadRequest;
import com.moud.net.protocol.AssetManifestRequest;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.AssetUploadBegin;
import com.moud.net.protocol.AssetUploadChunk;
import com.moud.net.protocol.AssetUploadComplete;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.SceneOpType;
import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.SceneSelect;
import com.moud.net.protocol.SceneSave;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ServerHello;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.RequestRespawn;
import com.moud.net.protocol.EditorModeChanged;
import com.moud.net.protocol.CursorState;
import com.moud.net.protocol.SceneCreate;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDelete;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.ProjectInfoRequest;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ScriptActionListRequest;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptActionInvoke;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.UiNodeEvent;
import com.moud.net.protocol.MultiMeshData;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WireMessages {
    private static final int MIN_ALLOC_BYTES = 256;
    private static final int MAX_ALLOC_BYTES = 1_048_576 + 64;

    private WireMessages() {
    }

    public static byte[] encode(Message message) {
        Objects.requireNonNull(message);
        int cap = clampAlloc(Math.max(MIN_ALLOC_BYTES, estimateSize(message)));
        for (int attempt = 0; attempt < 8; attempt++) {
            ByteBuffer out = ByteBuffer.allocate(cap);
            try {
                WireIo.writeVarInt(out, message.type().id());
                switch (message) {
                    case Hello hello -> WireIo.writeVarInt(out, hello.protocolVersion());
                    case ServerHello serverHello -> {
                        WireIo.writeVarInt(out, serverHello.protocolVersion());
                        WireIo.writeVarInt(out, serverHello.devMode() ? 1 : 0);
                    }
                    case Ping ping -> out.putLong(ping.nonce());
                    case Pong pong -> out.putLong(pong.nonce());
                    case SceneOpBatch batch -> writeSceneOpBatch(out, batch);
                    case SceneOpAck ack -> writeSceneOpAck(out, ack);
                    case SceneSnapshotRequest request -> writeLong(out, request.requestId());
                    case SceneSnapshot snapshot -> writeSceneSnapshot(out, snapshot);
                    case SchemaSnapshot schema -> writeSchemaSnapshot(out, schema);
                    case SceneList sceneList -> writeSceneList(out, sceneList);
                    case SceneSelect sceneSelect -> WireIo.writeString(out, sceneSelect.sceneId());
                    case SceneSave sceneSave -> WireIo.writeString(out, sceneSave.sceneId());
                    case SceneSaveAck ack -> writeSceneSaveAck(out, ack);
                    case AssetManifestRequest request -> writeLong(out, request.requestId());
                    case AssetManifestResponse response -> writeAssetManifestResponse(out, response);
                    case AssetUploadBegin begin -> writeAssetUploadBegin(out, begin);
                    case AssetUploadAck ack -> writeAssetUploadAck(out, ack);
                    case AssetUploadChunk chunk -> writeAssetUploadChunk(out, chunk);
                    case AssetUploadComplete complete -> writeAssetUploadComplete(out, complete);
                    case AssetDownloadRequest request -> writeAssetDownloadRequest(out, request);
                    case AssetDownloadBegin begin -> writeAssetDownloadBegin(out, begin);
                    case AssetDownloadChunk chunk -> writeAssetDownloadChunk(out, chunk);
                    case AssetDownloadComplete complete -> writeAssetDownloadComplete(out, complete);
                    case PlayerInput input -> writePlayerInput(out, input);
                    case RuntimeState state -> writeRuntimeState(out, state);
                    case CursorState state -> {
                        int flags = 0;
                        if (state.cursorModeEnabled()) flags |= 1;
                        if (state.osCursorVisible()) flags |= 2;
                        WireIo.writeVarInt(out, flags);
                    }
                    case RequestRespawn ignored -> {}
                    case EditorModeChanged msg -> WireIo.writeVarInt(out, msg.editorOpen() ? 1 : 0);
                    case SceneCreate msg -> {
                        WireIo.writeString(out, msg.sceneId());
                        WireIo.writeString(out, msg.displayName());
                    }
                    case SceneCreateAck msg -> {
                        WireIo.writeString(out, msg.sceneId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.error());
                    }
                    case SceneDelete msg -> WireIo.writeString(out, msg.sceneId());
                    case SceneDeleteAck msg -> {
                        WireIo.writeString(out, msg.sceneId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.error());
                    }
                    case ProjectInfoRequest msg -> writeLong(out, msg.requestId());
                    case ProjectInfo msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeVarInt(out, msg.exists() ? 1 : 0);
                        WireIo.writeString(out, msg.name());
                        WireIo.writeString(out, msg.author());
                    }
                    case ProjectCreate msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeString(out, msg.name());
                        WireIo.writeString(out, msg.author());
                    }
                    case ProjectCreateAck msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.error());
                        WireIo.writeString(out, msg.name());
                        WireIo.writeString(out, msg.author());
                    }
                    case ScriptActionListRequest msg -> {
                        writeLong(out, msg.requestId());
                        writeLong(out, msg.nodeId());
                    }
                    case ScriptActionListResponse msg -> writeScriptActionListResponse(out, msg);
                    case ScriptActionInvoke msg -> {
                        writeLong(out, msg.requestId());
                        writeLong(out, msg.nodeId());
                        WireIo.writeString(out, msg.action());
                    }
                    case ScriptActionInvokeAck msg -> {
                        writeLong(out, msg.requestId());
                        writeLong(out, msg.nodeId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.error());
                    }
                    case ScriptFileReadRequest msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeString(out, msg.path());
                    }
                    case ScriptFileReadResponse msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.path());
                        WireIo.writeString(out, msg.content());
                        WireIo.writeString(out, msg.error());
                    }
                    case ScriptFileWriteRequest msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeString(out, msg.path());
                        WireIo.writeString(out, msg.content());
                    }
                    case ScriptFileWriteAck msg -> {
                        writeLong(out, msg.requestId());
                        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
                        WireIo.writeString(out, msg.path());
                        WireIo.writeString(out, msg.error());
                    }
                    case UiNodeEvent msg -> {
                        writeLong(out, msg.nodeId());
                        WireIo.writeString(out, msg.event());
                        out.putFloat(msg.value());
                    }
                    case MultiMeshData msg -> {
                        writeLong(out, msg.nodeId());
                        WireIo.writeVarInt(out, msg.offset());
                        WireIo.writeVarInt(out, msg.total());
                        float[] data = msg.data();
                        int len = data == null ? 0 : data.length;
                        WireIo.writeVarInt(out, len);
                        if (data != null) {
                            for (float f : data) out.putFloat(f);
                        }
                    }
                    case PlayerMotion msg -> {
                        WireIo.writeVarInt(out, msg.mode());
                        out.putFloat(msg.x());
                        out.putFloat(msg.y());
                        out.putFloat(msg.z());
                        out.putFloat(msg.yawDeg());
                    }
                }
                out.flip();
                byte[] bytes = new byte[out.remaining()];
                out.get(bytes);
                return bytes;
            } catch (BufferOverflowException e) {
                // Estimation may under-shoot; retry with a larger buffer.
                int next = clampAlloc(Math.max(cap + 64, cap * 2));
                if (next <= cap) {
                    throw new IllegalArgumentException(
                            "Message too large to encode (cap=" + cap + "): " + message.type(), e);
                }
                cap = next;
            }
        }
        throw new IllegalArgumentException("Failed to encode message after retries: " + message.type());
    }

    public static Message decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        ByteBuffer in = ByteBuffer.wrap(bytes);
        int typeId = WireIo.readVarInt(in);
        MessageType type = MessageType.fromId(typeId);
        return switch (type) {
            case HELLO -> new Hello(WireIo.readVarInt(in));
            case SERVER_HELLO -> readServerHello(in);
            case PING -> new Ping(in.getLong());
            case PONG -> new Pong(in.getLong());
            case SCENE_OP_BATCH -> readSceneOpBatch(in);
            case SCENE_OP_ACK -> readSceneOpAck(in);
            case SCENE_SNAPSHOT_REQUEST -> new SceneSnapshotRequest(readLong(in));
            case SCENE_SNAPSHOT -> readSceneSnapshot(in);
            case SCHEMA_SNAPSHOT -> readSchemaSnapshot(in);
            case SCENE_LIST -> readSceneList(in);
            case SCENE_SELECT -> new SceneSelect(WireIo.readString(in));
            case SCENE_SAVE -> new SceneSave(WireIo.readString(in));
            case SCENE_SAVE_ACK -> readSceneSaveAck(in);
            case ASSET_MANIFEST_REQUEST -> new AssetManifestRequest(readLong(in));
            case ASSET_MANIFEST_RESPONSE -> readAssetManifestResponse(in);
            case ASSET_UPLOAD_BEGIN -> readAssetUploadBegin(in);
            case ASSET_UPLOAD_ACK -> readAssetUploadAck(in);
            case ASSET_UPLOAD_CHUNK -> readAssetUploadChunk(in);
            case ASSET_UPLOAD_COMPLETE -> readAssetUploadComplete(in);
            case ASSET_DOWNLOAD_REQUEST -> readAssetDownloadRequest(in);
            case ASSET_DOWNLOAD_BEGIN -> readAssetDownloadBegin(in);
            case ASSET_DOWNLOAD_CHUNK -> readAssetDownloadChunk(in);
            case ASSET_DOWNLOAD_COMPLETE -> readAssetDownloadComplete(in);
            case PLAYER_INPUT -> readPlayerInput(in);
            case RUNTIME_STATE -> readRuntimeState(in);
            case CURSOR_STATE -> {
                int flags = WireIo.readVarInt(in);
                yield new CursorState((flags & 1) != 0, (flags & 2) != 0);
            }
            case REQUEST_RESPAWN -> new RequestRespawn();
            case EDITOR_MODE_CHANGED -> new EditorModeChanged(WireIo.readVarInt(in) != 0);
            case UI_NODE_EVENT -> new UiNodeEvent(readLong(in), WireIo.readString(in), in.getFloat());
            case MULTIMESH_DATA -> {
                long nodeId = readLong(in);
                int offset = WireIo.readVarInt(in);
                int total = WireIo.readVarInt(in);
                int len = WireIo.readVarInt(in);
                float[] data = new float[Math.max(0, len)];
                for (int i = 0; i < data.length; i++) data[i] = in.getFloat();
                yield new MultiMeshData(nodeId, offset, total, data);
            }
            case SCENE_CREATE -> new SceneCreate(WireIo.readString(in), WireIo.readString(in));
            case SCENE_CREATE_ACK -> {
                String sceneId = WireIo.readString(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String error = WireIo.readString(in);
                yield new SceneCreateAck(sceneId, success, error);
            }
            case SCENE_DELETE -> new SceneDelete(WireIo.readString(in));
            case SCENE_DELETE_ACK -> {
                String sceneId = WireIo.readString(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String error = WireIo.readString(in);
                yield new SceneDeleteAck(sceneId, success, error);
            }
            case PROJECT_INFO_REQUEST -> new ProjectInfoRequest(readLong(in));
            case PROJECT_INFO -> {
                long requestId = readLong(in);
                boolean exists = WireIo.readVarInt(in) != 0;
                String name = WireIo.readString(in);
                String author = WireIo.readString(in);
                yield new ProjectInfo(requestId, exists, name, author);
            }
            case PROJECT_CREATE -> {
                long requestId = readLong(in);
                String name = WireIo.readString(in);
                String author = WireIo.readString(in);
                yield new ProjectCreate(requestId, name, author);
            }
            case PROJECT_CREATE_ACK -> {
                long requestId = readLong(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String error = WireIo.readString(in);
                String name = WireIo.readString(in);
                String author = WireIo.readString(in);
                yield new ProjectCreateAck(requestId, success, error, name, author);
            }
            case SCRIPT_ACTION_LIST_REQUEST -> new ScriptActionListRequest(readLong(in), readLong(in));
            case SCRIPT_ACTION_LIST_RESPONSE -> readScriptActionListResponse(in);
            case SCRIPT_ACTION_INVOKE -> {
                long requestId = readLong(in);
                long nodeId = readLong(in);
                String action = WireIo.readString(in);
                yield new ScriptActionInvoke(requestId, nodeId, action);
            }
            case SCRIPT_ACTION_INVOKE_ACK -> {
                long requestId = readLong(in);
                long nodeId = readLong(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String error = WireIo.readString(in);
                yield new ScriptActionInvokeAck(requestId, nodeId, success, error);
            }
            case SCRIPT_FILE_READ_REQUEST -> new ScriptFileReadRequest(readLong(in), WireIo.readString(in));
            case SCRIPT_FILE_READ_RESPONSE -> {
                long requestId = readLong(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String path = WireIo.readString(in);
                String content = WireIo.readString(in);
                String error = WireIo.readString(in);
                yield new ScriptFileReadResponse(requestId, success, path, content, error);
            }
            case SCRIPT_FILE_WRITE_REQUEST -> {
                long requestId = readLong(in);
                String path = WireIo.readString(in);
                String content = WireIo.readString(in);
                yield new ScriptFileWriteRequest(requestId, path, content);
            }
            case SCRIPT_FILE_WRITE_ACK -> {
                long requestId = readLong(in);
                boolean success = WireIo.readVarInt(in) != 0;
                String path = WireIo.readString(in);
                String error = WireIo.readString(in);
                yield new ScriptFileWriteAck(requestId, success, path, error);
            }
            case PLAYER_MOTION -> {
                int mode = WireIo.readVarInt(in);
                float x = in.getFloat();
                float y = in.getFloat();
                float z = in.getFloat();
                float yawDeg = in.getFloat();
                yield new PlayerMotion(mode, x, y, z, yawDeg);
            }
        };
    }

    private static ServerHello readServerHello(ByteBuffer in) {
        int protocolVersion = WireIo.readVarInt(in);
        boolean devMode = false;
        if (in.hasRemaining()) {
            devMode = WireIo.readVarInt(in) != 0;
        }
        return new ServerHello(protocolVersion, devMode);
    }

    private static void writePlayerInput(ByteBuffer out, PlayerInput input) {
        writeLong(out, input.clientTick());
        out.putFloat(input.moveX());
        out.putFloat(input.moveZ());
        out.putFloat(input.yawDeg());
        out.putFloat(input.pitchDeg());
        out.putFloat(input.cursorX());
        out.putFloat(input.cursorY());
        int flags = 0;
        if (input.jump()) {
            flags |= 1;
        }
        if (input.sprint()) {
            flags |= 2;
        }
        WireIo.writeVarInt(out, flags);
    }

    private static PlayerInput readPlayerInput(ByteBuffer in) {
        long tick = readLong(in);
        float moveX = in.getFloat();
        float moveZ = in.getFloat();
        float yaw = in.getFloat();
        float pitch = in.getFloat();
        float cursorX = in.getFloat();
        float cursorY = in.getFloat();
        int flags = WireIo.readVarInt(in);
        boolean jump = (flags & 1) != 0;
        boolean sprint = (flags & 2) != 0;
        return new PlayerInput(tick, moveX, moveZ, yaw, pitch, cursorX, cursorY, jump, sprint);
    }

    private static void writeSceneSaveAck(ByteBuffer out, SceneSaveAck ack) {
        WireIo.writeString(out, ack.sceneId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
    }

    private static SceneSaveAck readSceneSaveAck(ByteBuffer in) {
        String sceneId = WireIo.readString(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new SceneSaveAck(sceneId, success, error);
    }

    private static void writeRuntimeState(ByteBuffer out, RuntimeState state) {
        writeLong(out, state.serverTick());
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

    private static RuntimeState readRuntimeState(ByteBuffer in) {
        long serverTick = readLong(in);
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
        return new RuntimeState(serverTick, sceneId, fogEnabled,
                fogColorR, fogColorG, fogColorB, fogDensity,
                timeTicks, weather, ambientLight,
                useSceneCamera, sceneCamX, sceneCamY, sceneCamZ,
                sceneCamYawDeg, sceneCamPitchDeg, sceneCamRollDeg,
                useFollowCamera, followCamLocalX, followCamLocalY, followCamLocalZ,
                followCamPitchDeg, followCamRollDeg,
                useScriptCamera, scriptCamX, scriptCamY, scriptCamZ,
                scriptCamYawDeg, scriptCamPitchDeg, scriptCamRollDeg);
    }

    private static void writeScriptActionListResponse(ByteBuffer out, ScriptActionListResponse msg) {
        writeLong(out, msg.requestId());
        writeLong(out, msg.nodeId());
        WireIo.writeVarInt(out, msg.success() ? 1 : 0);
        WireIo.writeString(out, msg.error());
        List<String> actions = msg.actions() == null ? List.of() : msg.actions();
        WireIo.writeVarInt(out, actions.size());
        for (String action : actions) {
            WireIo.writeString(out, action);
        }
    }

    private static ScriptActionListResponse readScriptActionListResponse(ByteBuffer in) {
        long requestId = readLong(in);
        long nodeId = readLong(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 100_000) {
            throw new IllegalArgumentException("Invalid action count: " + count);
        }
        List<String> actions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            actions.add(WireIo.readString(in));
        }
        return new ScriptActionListResponse(requestId, nodeId, success, error, actions);
    }

    private static void writeBytes(ByteBuffer out, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        WireIo.writeVarInt(out, bytes.length);
        out.put(bytes);
    }

    private static byte[] readBytes(ByteBuffer in) {
        int len = WireIo.readVarInt(in);
        if (len < 0 || len > 1_048_576) {
            throw new IllegalArgumentException("Invalid bytes length: " + len);
        }
        if (in.remaining() < len) {
            throw new IllegalArgumentException("Bytes truncated");
        }
        byte[] bytes = new byte[len];
        in.get(bytes);
        return bytes;
    }

    private static void writeLong(ByteBuffer out, long value) {
        WireIo.writeVarInt(out, (int) (value >>> 32));
        WireIo.writeVarInt(out, (int) value);
    }

    private static long readLong(ByteBuffer in) {
        long hi = Integer.toUnsignedLong(WireIo.readVarInt(in));
        long lo = Integer.toUnsignedLong(WireIo.readVarInt(in));
        return (hi << 32) | lo;
    }

    private static void writeAssetType(ByteBuffer out, AssetType type) {
        WireIo.writeVarInt(out, type == null ? 0 : type.ordinal());
    }

    private static AssetType readAssetType(ByteBuffer in) {
        int ordinal = WireIo.readVarInt(in);
        AssetType[] values = AssetType.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return AssetType.BINARY;
        }
        return values[ordinal];
    }

    private static void writeStatus(ByteBuffer out, AssetTransferStatus status) {
        WireIo.writeVarInt(out, status == null ? AssetTransferStatus.ERROR.id() : status.id());
    }

    private static AssetTransferStatus readStatus(ByteBuffer in) {
        return AssetTransferStatus.fromId(WireIo.readVarInt(in));
    }

    private static ResPath readResPathOrNull(ByteBuffer in) {
        String value = WireIo.readString(in);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new ResPath(value);
    }

    private static AssetHash readHashOrNull(ByteBuffer in) {
        String value = WireIo.readString(in);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new AssetHash(value);
    }

    private static void writeAssetManifestResponse(ByteBuffer out, AssetManifestResponse response) {
        writeLong(out, response.requestId());
        List<AssetManifestResponse.Entry> entries = response.entries() == null ? List.of() : response.entries();
        WireIo.writeVarInt(out, entries.size());
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                WireIo.writeString(out, "");
                WireIo.writeString(out, "");
                writeLong(out, 0L);
                writeAssetType(out, AssetType.BINARY);
                continue;
            }
            AssetMeta meta = entry.meta();
            WireIo.writeString(out, entry.path().value());
            WireIo.writeString(out, meta.hash().hex());
            writeLong(out, meta.sizeBytes());
            writeAssetType(out, meta.type());
        }
    }

    private static AssetManifestResponse readAssetManifestResponse(ByteBuffer in) {
        long requestId = readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid asset manifest count: " + count);
        }
        List<AssetManifestResponse.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String path = WireIo.readString(in);
            String hash = WireIo.readString(in);
            long size = readLong(in);
            AssetType type = readAssetType(in);
            if (path == null || path.isBlank() || hash == null || hash.isBlank()) {
                continue;
            }
            entries.add(new AssetManifestResponse.Entry(
                    new ResPath(path),
                    new AssetMeta(new AssetHash(hash), size, type)
            ));
        }
        return new AssetManifestResponse(requestId, List.copyOf(entries));
    }

    private static void writeAssetUploadBegin(ByteBuffer out, AssetUploadBegin begin) {
        WireIo.writeString(out, begin.path() == null ? "" : begin.path().value());
        WireIo.writeString(out, begin.hash() == null ? "" : begin.hash().hex());
        writeLong(out, begin.sizeBytes());
        writeAssetType(out, begin.assetType());
    }

    private static AssetUploadBegin readAssetUploadBegin(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        long size = readLong(in);
        AssetType type = readAssetType(in);
        return new AssetUploadBegin(path, hash, size, type);
    }

    private static void writeAssetUploadAck(ByteBuffer out, AssetUploadAck ack) {
        WireIo.writeString(out, ack.path() == null ? "" : ack.path().value());
        WireIo.writeString(out, ack.hash() == null ? "" : ack.hash().hex());
        writeStatus(out, ack.status());
        WireIo.writeString(out, ack.message());
    }

    private static AssetUploadAck readAssetUploadAck(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetUploadAck(path, hash, status, message);
    }

    private static void writeAssetUploadChunk(ByteBuffer out, AssetUploadChunk chunk) {
        WireIo.writeString(out, chunk.hash().hex());
        WireIo.writeVarInt(out, chunk.index());
        writeBytes(out, chunk.bytes());
    }

    private static AssetUploadChunk readAssetUploadChunk(ByteBuffer in) {
        AssetHash hash = new AssetHash(WireIo.readString(in));
        int index = WireIo.readVarInt(in);
        byte[] bytes = readBytes(in);
        return new AssetUploadChunk(hash, index, bytes);
    }

    private static void writeAssetUploadComplete(ByteBuffer out, AssetUploadComplete complete) {
        WireIo.writeString(out, complete.path() == null ? "" : complete.path().value());
        WireIo.writeString(out, complete.hash() == null ? "" : complete.hash().hex());
    }

    private static AssetUploadComplete readAssetUploadComplete(ByteBuffer in) {
        ResPath path = readResPathOrNull(in);
        AssetHash hash = readHashOrNull(in);
        return new AssetUploadComplete(path, hash);
    }

    private static void writeAssetDownloadRequest(ByteBuffer out, AssetDownloadRequest request) {
        WireIo.writeString(out, request.hash() == null ? "" : request.hash().hex());
    }

    private static AssetDownloadRequest readAssetDownloadRequest(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        return new AssetDownloadRequest(hash);
    }

    private static void writeAssetDownloadBegin(ByteBuffer out, AssetDownloadBegin begin) {
        WireIo.writeString(out, begin.hash() == null ? "" : begin.hash().hex());
        writeLong(out, begin.sizeBytes());
        writeAssetType(out, begin.assetType());
        writeStatus(out, begin.status());
        WireIo.writeString(out, begin.message());
    }

    private static AssetDownloadBegin readAssetDownloadBegin(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        long size = readLong(in);
        AssetType type = readAssetType(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetDownloadBegin(hash, size, type, status, message);
    }

    private static void writeAssetDownloadChunk(ByteBuffer out, AssetDownloadChunk chunk) {
        WireIo.writeString(out, chunk.hash().hex());
        WireIo.writeVarInt(out, chunk.index());
        writeBytes(out, chunk.bytes());
    }

    private static AssetDownloadChunk readAssetDownloadChunk(ByteBuffer in) {
        AssetHash hash = new AssetHash(WireIo.readString(in));
        int index = WireIo.readVarInt(in);
        byte[] bytes = readBytes(in);
        return new AssetDownloadChunk(hash, index, bytes);
    }

    private static void writeAssetDownloadComplete(ByteBuffer out, AssetDownloadComplete complete) {
        WireIo.writeString(out, complete.hash() == null ? "" : complete.hash().hex());
        writeStatus(out, complete.status());
        WireIo.writeString(out, complete.message());
    }

    private static AssetDownloadComplete readAssetDownloadComplete(ByteBuffer in) {
        AssetHash hash = readHashOrNull(in);
        AssetTransferStatus status = readStatus(in);
        String message = WireIo.readString(in);
        return new AssetDownloadComplete(hash, status, message);
    }

    private static void writeSceneOpBatch(ByteBuffer out, SceneOpBatch batch) {
        writeLong(out, batch.batchId());
        WireIo.writeVarInt(out, batch.atomic() ? 1 : 0);
        List<SceneOp> ops = batch.ops();
        WireIo.writeVarInt(out, ops.size());
        for (SceneOp op : ops) {
            WireIo.writeVarInt(out, op.type().id());
            switch (op) {
                case SceneOp.CreateNode createNode -> {
                    writeLong(out, createNode.parentId());
                    WireIo.writeString(out, createNode.name());
                    WireIo.writeString(out, createNode.typeId());
                }
                case SceneOp.QueueFree queueFree -> writeLong(out, queueFree.nodeId());
                case SceneOp.Rename rename -> {
                    writeLong(out, rename.nodeId());
                    WireIo.writeString(out, rename.newName());
                }
                case SceneOp.SetProperty setProperty -> {
                    writeLong(out, setProperty.nodeId());
                    WireIo.writeString(out, setProperty.key());
                    WireIo.writeString(out, setProperty.value());
                }
                case SceneOp.RemoveProperty removeProperty -> {
                    writeLong(out, removeProperty.nodeId());
                    WireIo.writeString(out, removeProperty.key());
                }
                case SceneOp.Reparent reparent -> {
                    writeLong(out, reparent.nodeId());
                    writeLong(out, reparent.newParentId());
                    WireIo.writeVarInt(out, reparent.index());
                }
            }
        }
    }

    private static SceneOpBatch readSceneOpBatch(ByteBuffer in) {
        long batchId = readLong(in);
        boolean atomic = WireIo.readVarInt(in) != 0;
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid op count: " + count);
        }
        List<SceneOp> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SceneOpType opType = SceneOpType.fromId(WireIo.readVarInt(in));
            SceneOp op = switch (opType) {
                case CREATE_NODE -> new SceneOp.CreateNode(readLong(in), WireIo.readString(in), WireIo.readString(in));
                case QUEUE_FREE -> new SceneOp.QueueFree(readLong(in));
                case RENAME -> new SceneOp.Rename(readLong(in), WireIo.readString(in));
                case SET_PROPERTY ->
                        new SceneOp.SetProperty(readLong(in), WireIo.readString(in), WireIo.readString(in));
                case REMOVE_PROPERTY -> new SceneOp.RemoveProperty(readLong(in), WireIo.readString(in));
                case REPARENT -> new SceneOp.Reparent(readLong(in), readLong(in), WireIo.readVarInt(in));
            };
            ops.add(op);
        }
        return new SceneOpBatch(batchId, atomic, List.copyOf(ops));
    }

    private static void writeSceneOpAck(ByteBuffer out, SceneOpAck ack) {
        writeLong(out, ack.batchId());
        writeLong(out, ack.sceneRevision());
        List<SceneOpResult> results = ack.results();
        WireIo.writeVarInt(out, results.size());
        for (SceneOpResult result : results) {
            writeLong(out, result.targetId());
            writeLong(out, result.createdId());
            WireIo.writeVarInt(out, result.ok() ? 1 : 0);
            WireIo.writeVarInt(out, result.error().id());
            WireIo.writeString(out, result.message());
        }
    }

    private static SceneOpAck readSceneOpAck(ByteBuffer in) {
        long batchId = readLong(in);
        long revision = readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid result count: " + count);
        }
        List<SceneOpResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long targetId = readLong(in);
            long createdId = readLong(in);
            boolean ok = WireIo.readVarInt(in) != 0;
            SceneOpError error = SceneOpError.fromId(WireIo.readVarInt(in));
            String message = WireIo.readString(in);
            results.add(new SceneOpResult(targetId, createdId, ok, error, message));
        }
        return new SceneOpAck(batchId, revision, List.copyOf(results));
    }

    private static void writeSceneSnapshot(ByteBuffer out, SceneSnapshot snapshot) {
        writeLong(out, snapshot.requestId());
        writeLong(out, snapshot.revision());
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        WireIo.writeVarInt(out, nodes.size());
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            writeLong(out, node.nodeId());
            writeLong(out, node.parentId());
            WireIo.writeString(out, node.name());
            WireIo.writeString(out, node.type());
            List<SceneSnapshot.Property> props = node.properties();
            WireIo.writeVarInt(out, props.size());
            for (SceneSnapshot.Property prop : props) {
                WireIo.writeString(out, prop.key());
                WireIo.writeString(out, prop.value());
            }
            List<SceneSnapshot.Uniform> uniforms = node.uniforms();
            WireIo.writeVarInt(out, uniforms.size());
            for (SceneSnapshot.Uniform u : uniforms) {
                WireIo.writeString(out, u.key());
                List<Float> vals = u.values();
                WireIo.writeVarInt(out, vals.size());
                for (float v : vals) out.putFloat(v);
            }
        }
    }

    private static void writeSceneList(ByteBuffer out, SceneList list) {
        List<SceneInfo> scenes = list.scenes() == null ? List.of() : list.scenes();
        WireIo.writeVarInt(out, scenes.size());
        for (SceneInfo scene : scenes) {
            if (scene == null) {
                WireIo.writeString(out, "");
                WireIo.writeString(out, "");
                continue;
            }
            WireIo.writeString(out, scene.sceneId());
            WireIo.writeString(out, scene.displayName());
        }
        WireIo.writeString(out, list.activeSceneId() == null ? "" : list.activeSceneId());
    }

    private static SceneList readSceneList(ByteBuffer in) {
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 100_000) {
            throw new IllegalArgumentException("Invalid scene count: " + count);
        }
        ArrayList<SceneInfo> scenes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = WireIo.readString(in);
            String name = WireIo.readString(in);
            if (id == null || id.isBlank()) {
                continue;
            }
            scenes.add(new SceneInfo(id, name));
        }
        String active = WireIo.readString(in);
        return new SceneList(List.copyOf(scenes), active == null ? "" : active);
    }

    private static SceneSnapshot readSceneSnapshot(ByteBuffer in) {
        long requestId = readLong(in);
        long revision = readLong(in);
        int nodeCount = WireIo.readVarInt(in);
        if (nodeCount < 0 || nodeCount > 2_000_000) {
            throw new IllegalArgumentException("Invalid node count: " + nodeCount);
        }
        List<SceneSnapshot.NodeSnapshot> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            long nodeId = readLong(in);
            long parentId = readLong(in);
            String name = WireIo.readString(in);
            String type = WireIo.readString(in);
            int propCount = WireIo.readVarInt(in);
            if (propCount < 0 || propCount > 1_000_000) {
                throw new IllegalArgumentException("Invalid property count: " + propCount);
            }
            List<SceneSnapshot.Property> props = new ArrayList<>(propCount);
            for (int p = 0; p < propCount; p++) {
                props.add(new SceneSnapshot.Property(WireIo.readString(in), WireIo.readString(in)));
            }
            int uniCount = WireIo.readVarInt(in);
            List<SceneSnapshot.Uniform> uniforms = new ArrayList<>(uniCount);
            for (int u = 0; u < uniCount; u++) {
                String key = WireIo.readString(in);
                int valCount = WireIo.readVarInt(in);
                List<Float> vals = new ArrayList<>(valCount);
                for (int v = 0; v < valCount; v++) vals.add(in.getFloat());
                uniforms.add(new SceneSnapshot.Uniform(key, List.copyOf(vals)));
            }
            nodes.add(new SceneSnapshot.NodeSnapshot(nodeId, parentId, name, type, List.copyOf(props), List.copyOf(uniforms)));
        }
        return new SceneSnapshot(requestId, revision, List.copyOf(nodes));
    }

    private static void writeSchemaSnapshot(ByteBuffer out, SchemaSnapshot snapshot) {
        writeLong(out, snapshot.schemaRevision());
        List<NodeTypeDef> types = snapshot.types();
        WireIo.writeVarInt(out, types.size());
        for (NodeTypeDef type : types) {
            WireIo.writeString(out, type.typeId());
            WireIo.writeString(out, type.displayName());
            WireIo.writeString(out, type.category());
            WireIo.writeVarInt(out, type.order());

            var props = type.properties();
            ArrayList<PropertyDef> propList = new ArrayList<>(props.values());
            propList.sort(Comparator
                    .comparing(PropertyDef::category)
                    .thenComparingInt(PropertyDef::order)
                    .thenComparing(PropertyDef::uiLabel)
                    .thenComparing(PropertyDef::key));
            WireIo.writeVarInt(out, propList.size());
            for (PropertyDef prop : propList) {
                WireIo.writeString(out, prop.key());
                WireIo.writeString(out, prop.type().name());

                String dv = prop.defaultValue();
                WireIo.writeVarInt(out, dv == null ? 0 : 1);
                if (dv != null) {
                    WireIo.writeString(out, dv);
                }

                WireIo.writeString(out, prop.displayName());
                WireIo.writeString(out, prop.category());
                WireIo.writeVarInt(out, prop.order());

                var hints = prop.editorHints();
                WireIo.writeVarInt(out, hints.size());
                for (var entry : hints.entrySet()) {
                    WireIo.writeString(out, entry.getKey());
                    WireIo.writeString(out, entry.getValue());
                }
            }
        }
    }

    private static SchemaSnapshot readSchemaSnapshot(ByteBuffer in) {
        long rev = readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid type count: " + count);
        }
        List<NodeTypeDef> typeDefs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String typeId = WireIo.readString(in);
            String displayName = WireIo.readString(in);
            String category = WireIo.readString(in);
            int order = WireIo.readVarInt(in);

            int propCount = WireIo.readVarInt(in);
            if (propCount < 0 || propCount > 1_000_000) {
                throw new IllegalArgumentException("Invalid property count: " + propCount);
            }
            Map<String, PropertyDef> props = new LinkedHashMap<>();
            for (int p = 0; p < propCount; p++) {
                String key = WireIo.readString(in);
                String typeName = WireIo.readString(in);
                PropertyType type;
                try {
                    type = PropertyType.valueOf(typeName);
                } catch (Exception e) {
                    type = PropertyType.STRING;
                }

                boolean hasDefault = WireIo.readVarInt(in) != 0;
                String defaultValue = hasDefault ? WireIo.readString(in) : null;
                String propDisplayName = WireIo.readString(in);
                String propCategory = WireIo.readString(in);
                int propOrder = WireIo.readVarInt(in);

                int hintCount = WireIo.readVarInt(in);
                if (hintCount < 0 || hintCount > 1_000_000) {
                    throw new IllegalArgumentException("Invalid hint count: " + hintCount);
                }
                Map<String, String> hints = new LinkedHashMap<>();
                for (int h = 0; h < hintCount; h++) {
                    hints.put(WireIo.readString(in), WireIo.readString(in));
                }

                props.put(key, new PropertyDef(key, type, defaultValue, propDisplayName, propCategory, propOrder, hints));
            }
            typeDefs.add(new NodeTypeDef(typeId, displayName, category, order, props));
        }
        return new SchemaSnapshot(rev, List.copyOf(typeDefs));
    }

    private static int estimateSize(Message message) {
        int size = 0;
        size += varIntSize(message.type().id());
        switch (message) {
            case Hello hello -> size += varIntSize(hello.protocolVersion());
            case ServerHello serverHello -> size += varIntSize(serverHello.protocolVersion());
            case Ping ignored -> size += Long.BYTES;
            case Pong ignored -> size += Long.BYTES;
            case SceneSnapshotRequest request -> size += longSize(request.requestId());
            case SceneOpBatch batch -> size += estimateSceneOpBatchSize(batch);
            case SceneOpAck ack -> size += estimateSceneOpAckSize(ack);
            case SceneSnapshot snapshot -> size += estimateSceneSnapshotSize(snapshot);
            case SchemaSnapshot schema -> size += estimateSchemaSnapshotSize(schema);
            case SceneList list -> size += estimateSceneListSize(list);
            case SceneSelect select -> size += stringSize(select.sceneId());
            case AssetManifestRequest request -> size += longSize(request.requestId());
            case AssetManifestResponse response -> size += estimateAssetManifestResponseSize(response);
            case AssetUploadBegin begin -> size += estimateAssetUploadBeginSize(begin);
            case AssetUploadAck ack -> size += estimateAssetUploadAckSize(ack);
            case AssetUploadChunk chunk -> size += estimateAssetUploadChunkSize(chunk);
            case AssetUploadComplete complete -> size += estimateAssetUploadCompleteSize(complete);
            case AssetDownloadRequest request -> size += estimateAssetDownloadRequestSize(request);
            case AssetDownloadBegin begin -> size += estimateAssetDownloadBeginSize(begin);
            case AssetDownloadChunk chunk -> size += estimateAssetDownloadChunkSize(chunk);
            case AssetDownloadComplete complete -> size += estimateAssetDownloadCompleteSize(complete);
            case PlayerInput input -> size += estimatePlayerInputSize(input);
            case SceneSave save -> size += estimateSceneSaveSize(save);
            case SceneSaveAck ack -> size += estimateSceneSaveAckSize(ack);
            case RuntimeState state -> size += estimateRuntimeStateSize(state);
            case RequestRespawn ignored -> {}
            case EditorModeChanged ignored -> size += varIntSize(1);
            case SceneCreate msg -> size += stringSize(msg.sceneId()) + stringSize(msg.displayName());
            case SceneCreateAck msg -> size += stringSize(msg.sceneId()) + varIntSize(1) + stringSize(msg.error());
            case SceneDelete msg -> size += stringSize(msg.sceneId());
            case SceneDeleteAck msg -> size += stringSize(msg.sceneId()) + varIntSize(1) + stringSize(msg.error());
            case ProjectInfoRequest msg -> size += longSize(msg.requestId());
            case ProjectInfo msg -> size += longSize(msg.requestId()) + varIntSize(1) + stringSize(msg.name()) + stringSize(msg.author());
            case ProjectCreate msg -> size += longSize(msg.requestId()) + stringSize(msg.name()) + stringSize(msg.author());
            case ProjectCreateAck msg -> size += longSize(msg.requestId()) + varIntSize(1) + stringSize(msg.error()) + stringSize(msg.name()) + stringSize(msg.author());
            case ScriptActionListRequest msg -> size += longSize(msg.requestId()) + longSize(msg.nodeId());
            case ScriptActionListResponse msg -> {
                size += longSize(msg.requestId()) + longSize(msg.nodeId()) + varIntSize(1) + stringSize(msg.error());
                List<String> actions = msg.actions() == null ? List.of() : msg.actions();
                size += varIntSize(actions.size());
                for (String a : actions) size += stringSize(a);
            }
            case ScriptActionInvoke msg -> size += longSize(msg.requestId()) + longSize(msg.nodeId()) + stringSize(msg.action());
            case ScriptActionInvokeAck msg -> size += longSize(msg.requestId()) + longSize(msg.nodeId()) + varIntSize(1) + stringSize(msg.error());
            case ScriptFileReadRequest msg -> size += longSize(msg.requestId()) + stringSize(msg.path());
            case ScriptFileReadResponse msg -> size += longSize(msg.requestId()) + varIntSize(1) + stringSize(msg.path()) + stringSize(msg.content()) + stringSize(msg.error());
            case ScriptFileWriteRequest msg -> size += longSize(msg.requestId()) + stringSize(msg.path()) + stringSize(msg.content());
            case ScriptFileWriteAck msg -> size += longSize(msg.requestId()) + varIntSize(1) + stringSize(msg.path()) + stringSize(msg.error());
            case UiNodeEvent msg -> size += longSize(msg.nodeId()) + stringSize(msg.event()) + Float.BYTES;
            case MultiMeshData msg -> size += longSize(msg.nodeId()) + varIntSize(msg.offset()) + varIntSize(msg.total()) + varIntSize(msg.data() == null ? 0 : msg.data().length) + (msg.data() == null ? 0 : msg.data().length * Float.BYTES);
            case PlayerMotion ignored -> size += varIntSize(2) + 4 * Float.BYTES;
            case CursorState ignored -> size += varIntSize(3);
        }
        return size + 16;
    }

    private static int estimateSceneSaveSize(SceneSave save) {
        return stringSize(save.sceneId());
    }

    private static int estimateSceneSaveAckSize(SceneSaveAck ack) {
        int size = 0;
        size += stringSize(ack.sceneId());
        size += varIntSize(ack.success() ? 1 : 0);
        size += stringSize(ack.error());
        return size;
    }

    private static int estimatePlayerInputSize(PlayerInput input) {
        int size = 0;
        size += longSize(input.clientTick());
        size += 4 * 4;
        size += varIntSize(0);
        return size;
    }

    private static int estimateRuntimeStateSize(RuntimeState state) {
        int size = 0;
        size += longSize(state.serverTick());
        size += stringSize(state.sceneId());
        size += varIntSize(1); // fogEnabled
        size += 4 * 4; // fogColorR/G/B + fogDensity
        size += varIntSize(state.timeTicks());
        size += stringSize(state.weather());
        size += 4; // ambientLight
        size += varIntSize(1); // useSceneCamera
        size += 6 * 4; // sceneCamX/Y/Z + Yaw/Pitch/Roll
        return size;
    }

    private static int estimateAssetManifestResponseSize(AssetManifestResponse response) {
        int size = 0;
        size += longSize(response.requestId());
        List<AssetManifestResponse.Entry> entries = response.entries() == null ? List.of() : response.entries();
        size += varIntSize(entries.size());
        for (AssetManifestResponse.Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.meta() == null) {
                size += stringSize("") + stringSize("") + longSize(0L) + varIntSize(0);
                continue;
            }
            size += stringSize(entry.path().value());
            size += stringSize(entry.meta().hash().hex());
            size += longSize(entry.meta().sizeBytes());
            size += varIntSize(entry.meta().type().ordinal());
        }
        return size;
    }

    private static int estimateAssetUploadBeginSize(AssetUploadBegin begin) {
        int size = 0;
        size += stringSize(begin.path() == null ? "" : begin.path().value());
        size += stringSize(begin.hash() == null ? "" : begin.hash().hex());
        size += longSize(begin.sizeBytes());
        size += varIntSize(begin.assetType() == null ? 0 : begin.assetType().ordinal());
        return size;
    }

    private static int estimateAssetUploadAckSize(AssetUploadAck ack) {
        int size = 0;
        size += stringSize(ack.path() == null ? "" : ack.path().value());
        size += stringSize(ack.hash() == null ? "" : ack.hash().hex());
        size += varIntSize(ack.status() == null ? AssetTransferStatus.ERROR.id() : ack.status().id());
        size += stringSize(ack.message());
        return size;
    }

    private static int estimateAssetUploadChunkSize(AssetUploadChunk chunk) {
        int size = 0;
        size += stringSize(chunk.hash() == null ? "" : chunk.hash().hex());
        size += varIntSize(chunk.index());
        size += varIntSize(chunk.bytes() == null ? 0 : chunk.bytes().length) + (chunk.bytes() == null ? 0 : chunk.bytes().length);
        return size;
    }

    private static int estimateAssetUploadCompleteSize(AssetUploadComplete complete) {
        int size = 0;
        size += stringSize(complete.path() == null ? "" : complete.path().value());
        size += stringSize(complete.hash() == null ? "" : complete.hash().hex());
        return size;
    }

    private static int estimateAssetDownloadRequestSize(AssetDownloadRequest request) {
        return stringSize(request.hash() == null ? "" : request.hash().hex());
    }

    private static int estimateAssetDownloadBeginSize(AssetDownloadBegin begin) {
        int size = 0;
        size += stringSize(begin.hash() == null ? "" : begin.hash().hex());
        size += longSize(begin.sizeBytes());
        size += varIntSize(begin.assetType() == null ? 0 : begin.assetType().ordinal());
        size += varIntSize(begin.status() == null ? AssetTransferStatus.ERROR.id() : begin.status().id());
        size += stringSize(begin.message());
        return size;
    }

    private static int estimateAssetDownloadChunkSize(AssetDownloadChunk chunk) {
        int size = 0;
        size += stringSize(chunk.hash() == null ? "" : chunk.hash().hex());
        size += varIntSize(chunk.index());
        size += varIntSize(chunk.bytes() == null ? 0 : chunk.bytes().length) + (chunk.bytes() == null ? 0 : chunk.bytes().length);
        return size;
    }

    private static int estimateAssetDownloadCompleteSize(AssetDownloadComplete complete) {
        int size = 0;
        size += stringSize(complete.hash() == null ? "" : complete.hash().hex());
        size += varIntSize(complete.status() == null ? AssetTransferStatus.ERROR.id() : complete.status().id());
        size += stringSize(complete.message());
        return size;
    }

    private static int estimateSceneListSize(SceneList list) {
        int size = 0;
        List<SceneInfo> scenes = list.scenes() == null ? List.of() : list.scenes();
        size += varIntSize(scenes.size());
        for (SceneInfo scene : scenes) {
            if (scene == null) {
                size += stringSize("") + stringSize("");
                continue;
            }
            size += stringSize(scene.sceneId());
            size += stringSize(scene.displayName());
        }
        size += stringSize(list.activeSceneId());
        return size;
    }

    private static int estimateSceneOpBatchSize(SceneOpBatch batch) {
        int size = 0;
        size += longSize(batch.batchId());
        size += varIntSize(batch.atomic() ? 1 : 0);
        List<SceneOp> ops = batch.ops();
        size += varIntSize(ops.size());
        for (SceneOp op : ops) {
            size += varIntSize(op.type().id());
            switch (op) {
                case SceneOp.CreateNode createNode ->
                        size += longSize(createNode.parentId()) + stringSize(createNode.name()) + stringSize(createNode.typeId());
                case SceneOp.QueueFree queueFree -> size += longSize(queueFree.nodeId());
                case SceneOp.Rename rename -> size += longSize(rename.nodeId()) + stringSize(rename.newName());
                case SceneOp.SetProperty setProperty ->
                        size += longSize(setProperty.nodeId()) + stringSize(setProperty.key()) + stringSize(setProperty.value());
                case SceneOp.RemoveProperty removeProperty ->
                        size += longSize(removeProperty.nodeId()) + stringSize(removeProperty.key());
                case SceneOp.Reparent reparent ->
                        size += longSize(reparent.nodeId()) + longSize(reparent.newParentId()) + varIntSize(reparent.index());
            }
        }
        return size;
    }

    private static int estimateSchemaSnapshotSize(SchemaSnapshot snapshot) {
        int size = 0;
        size += longSize(snapshot.schemaRevision());
        List<NodeTypeDef> types = snapshot.types();
        size += varIntSize(types.size());
        for (NodeTypeDef type : types) {
            size += stringSize(type.typeId());
            size += stringSize(type.displayName());
            size += stringSize(type.category());
            size += varIntSize(type.order());

            var props = type.properties();
            size += varIntSize(props.size());
            for (PropertyDef prop : props.values()) {
                size += stringSize(prop.key());
                size += stringSize(prop.type().name());

                String dv = prop.defaultValue();
                size += varIntSize(dv == null ? 0 : 1);
                if (dv != null) {
                    size += stringSize(dv);
                }

                size += stringSize(prop.displayName());
                size += stringSize(prop.category());
                size += varIntSize(prop.order());

                var hints = prop.editorHints();
                size += varIntSize(hints.size());
                for (var entry : hints.entrySet()) {
                    size += stringSize(entry.getKey());
                    size += stringSize(entry.getValue());
                }
            }
        }
        return size;
    }

    private static int estimateSceneOpAckSize(SceneOpAck ack) {
        int size = 0;
        size += longSize(ack.batchId());
        size += longSize(ack.sceneRevision());
        List<SceneOpResult> results = ack.results();
        size += varIntSize(results.size());
        for (SceneOpResult result : results) {
            size += longSize(result.targetId());
            size += longSize(result.createdId());
            size += varIntSize(result.ok() ? 1 : 0);
            size += varIntSize(result.error().id());
            size += stringSize(result.message());
        }
        return size;
    }

    private static int estimateSceneSnapshotSize(SceneSnapshot snapshot) {
        int size = 0;
        size += longSize(snapshot.requestId());
        size += longSize(snapshot.revision());
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        size += varIntSize(nodes.size());
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            size += longSize(node.nodeId());
            size += longSize(node.parentId());
            size += stringSize(node.name());
            size += stringSize(node.type());
            List<SceneSnapshot.Property> props = node.properties();
            size += varIntSize(props.size());
            for (SceneSnapshot.Property prop : props) {
                size += stringSize(prop.key());
                size += stringSize(prop.value());
            }
            List<SceneSnapshot.Uniform> uniforms = node.uniforms();
            size += varIntSize(uniforms.size());
            for (SceneSnapshot.Uniform u : uniforms) {
                size += stringSize(u.key());
                size += varIntSize(u.values().size());
                size += u.values().size() * Float.BYTES;
            }
        }
        return size;
    }

    private static int longSize(long value) {
        return varIntSize((int) (value >>> 32)) + varIntSize((int) value);
    }

    private static int stringSize(String value) {
        int len = utf8Length(value == null ? "" : value);
        return varIntSize(len) + len;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    private static int utf8Length(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int len = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7F) {
                len += 1;
                continue;
            }
            if (c <= 0x7FF) {
                len += 2;
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    len += 4;
                    i++;
                } else {
                    len += 3;
                }
                continue;
            }
            len += 3;
        }
        return len;
    }

    private static int clampAlloc(int cap) {
        if (cap <= 0) {
            return MIN_ALLOC_BYTES;
        }
        return Math.min(MAX_ALLOC_BYTES, cap);
    }
}
