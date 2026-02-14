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
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ServerHello;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WireMessages {
    private static final int MIN_ALLOC_BYTES = 256;
    // Keep aligned with TransportFrames MAX payload (1 MiB).
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
                    case ServerHello serverHello -> WireIo.writeVarInt(out, serverHello.protocolVersion());
                    case Ping ping -> out.putLong(ping.nonce());
                    case Pong pong -> out.putLong(pong.nonce());
                    case SceneOpBatch batch -> writeSceneOpBatch(out, batch);
                    case SceneOpAck ack -> writeSceneOpAck(out, ack);
                    case SceneSnapshotRequest request -> writeLong(out, request.requestId());
                    case SceneSnapshot snapshot -> writeSceneSnapshot(out, snapshot);
                    case SchemaSnapshot schema -> writeSchemaSnapshot(out, schema);
                    case SceneList sceneList -> writeSceneList(out, sceneList);
                    case SceneSelect sceneSelect -> WireIo.writeString(out, sceneSelect.sceneId());
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
            case SERVER_HELLO -> new ServerHello(WireIo.readVarInt(in));
            case PING -> new Ping(in.getLong());
            case PONG -> new Pong(in.getLong());
            case SCENE_OP_BATCH -> readSceneOpBatch(in);
            case SCENE_OP_ACK -> readSceneOpAck(in);
            case SCENE_SNAPSHOT_REQUEST -> new SceneSnapshotRequest(readLong(in));
            case SCENE_SNAPSHOT -> readSceneSnapshot(in);
            case SCHEMA_SNAPSHOT -> readSchemaSnapshot(in);
            case SCENE_LIST -> readSceneList(in);
            case SCENE_SELECT -> new SceneSelect(WireIo.readString(in));
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
        };
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
            nodes.add(new SceneSnapshot.NodeSnapshot(nodeId, parentId, name, type, List.copyOf(props)));
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
            propList.sort(java.util.Comparator
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
            java.util.Map<String, PropertyDef> props = new java.util.LinkedHashMap<>();
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
                java.util.Map<String, String> hints = new java.util.LinkedHashMap<>();
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
        }
        return size + 16;
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
