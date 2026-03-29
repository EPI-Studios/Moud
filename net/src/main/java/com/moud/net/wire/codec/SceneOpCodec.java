package com.moud.net.wire.codec;

import com.moud.net.protocol.SceneCreate;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDelete;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.SceneOpType;
import com.moud.net.protocol.SceneSave;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.wire.WireIo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SceneOpCodec {
    private SceneOpCodec() {
    }

    public static void writeSceneSaveAck(ByteBuffer out, SceneSaveAck ack) {
        WireIo.writeString(out, ack.sceneId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
    }

    public static SceneSaveAck readSceneSaveAck(ByteBuffer in) {
        String sceneId = WireIo.readString(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new SceneSaveAck(sceneId, success, error);
    }

    public static void writeSceneCreate(ByteBuffer out, SceneCreate create) {
        WireIo.writeString(out, create.sceneId());
        WireIo.writeString(out, create.displayName());
    }

    public static SceneCreate readSceneCreate(ByteBuffer in) {
        String sceneId = WireIo.readString(in);
        String displayName = WireIo.readString(in);
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
        return new SceneCreate(sceneId, displayName);
    }

    public static void writeSceneDelete(ByteBuffer out, SceneDelete delete) {
        WireIo.writeString(out, delete.sceneId());
    }

    public static SceneDelete readSceneDelete(ByteBuffer in) {
        return new SceneDelete(WireIo.readString(in));
    }

    public static void writeSceneCreateAck(ByteBuffer out, SceneCreateAck ack) {
        WireIo.writeString(out, ack.sceneId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
    }

    public static SceneCreateAck readSceneCreateAck(ByteBuffer in) {
        String sceneId = WireIo.readString(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new SceneCreateAck(sceneId, success, error);
    }

    public static void writeSceneDeleteAck(ByteBuffer out, SceneDeleteAck ack) {
        WireIo.writeString(out, ack.sceneId());
        WireIo.writeVarInt(out, ack.success() ? 1 : 0);
        WireIo.writeString(out, ack.error());
    }

    public static SceneDeleteAck readSceneDeleteAck(ByteBuffer in) {
        String sceneId = WireIo.readString(in);
        boolean success = WireIo.readVarInt(in) != 0;
        String error = WireIo.readString(in);
        if (error != null && error.isBlank()) {
            error = null;
        }
        return new SceneDeleteAck(sceneId, success, error);
    }

    public static void writeSceneOpBatch(ByteBuffer out, SceneOpBatch batch) {
        WireIo.writeLong(out, batch.batchId());
        WireIo.writeVarInt(out, batch.atomic() ? 1 : 0);
        List<SceneOp> ops = batch.ops();
        WireIo.writeVarInt(out, ops.size());
        for (SceneOp op : ops) {
            WireIo.writeVarInt(out, op.type().id());
            switch (op) {
                case SceneOp.CreateNode createNode -> {
                    WireIo.writeLong(out, createNode.parentId());
                    WireIo.writeString(out, createNode.name());
                    WireIo.writeString(out, createNode.typeId());
                }
                case SceneOp.QueueFree queueFree -> WireIo.writeLong(out, queueFree.nodeId());
                case SceneOp.Rename rename -> {
                    WireIo.writeLong(out, rename.nodeId());
                    WireIo.writeString(out, rename.newName());
                }
                case SceneOp.SetProperty setProperty -> {
                    WireIo.writeLong(out, setProperty.nodeId());
                    WireIo.writeString(out, setProperty.key());
                    WireIo.writeString(out, setProperty.value());
                }
                case SceneOp.RemoveProperty removeProperty -> {
                    WireIo.writeLong(out, removeProperty.nodeId());
                    WireIo.writeString(out, removeProperty.key());
                }
                case SceneOp.Reparent reparent -> {
                    WireIo.writeLong(out, reparent.nodeId());
                    WireIo.writeLong(out, reparent.newParentId());
                    WireIo.writeVarInt(out, reparent.index());
                }
            }
        }
    }

    public static SceneOpBatch readSceneOpBatch(ByteBuffer in) {
        long batchId = WireIo.readLong(in);
        boolean atomic = WireIo.readVarInt(in) != 0;
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid op count: " + count);
        }
        List<SceneOp> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SceneOpType opType = SceneOpType.fromId(WireIo.readVarInt(in));
            SceneOp op = switch (opType) {
                case CREATE_NODE -> new SceneOp.CreateNode(WireIo.readLong(in), WireIo.readString(in), WireIo.readString(in));
                case QUEUE_FREE -> new SceneOp.QueueFree(WireIo.readLong(in));
                case RENAME -> new SceneOp.Rename(WireIo.readLong(in), WireIo.readString(in));
                case SET_PROPERTY -> new SceneOp.SetProperty(WireIo.readLong(in), WireIo.readString(in), WireIo.readString(in));
                case REMOVE_PROPERTY -> new SceneOp.RemoveProperty(WireIo.readLong(in), WireIo.readString(in));
                case REPARENT -> new SceneOp.Reparent(WireIo.readLong(in), WireIo.readLong(in), WireIo.readVarInt(in));
            };
            ops.add(op);
        }
        return new SceneOpBatch(batchId, atomic, List.copyOf(ops));
    }

    public static void writeSceneOpAck(ByteBuffer out, SceneOpAck ack) {
        WireIo.writeLong(out, ack.batchId());
        WireIo.writeLong(out, ack.sceneRevision());
        List<SceneOpResult> results = ack.results();
        WireIo.writeVarInt(out, results.size());
        for (SceneOpResult result : results) {
            WireIo.writeLong(out, result.targetId());
            WireIo.writeLong(out, result.createdId());
            WireIo.writeVarInt(out, result.ok() ? 1 : 0);
            WireIo.writeVarInt(out, result.error().id());
            WireIo.writeString(out, result.message());
        }
    }

    public static SceneOpAck readSceneOpAck(ByteBuffer in) {
        long batchId = WireIo.readLong(in);
        long revision = WireIo.readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid result count: " + count);
        }
        List<SceneOpResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long targetId = WireIo.readLong(in);
            long createdId = WireIo.readLong(in);
            boolean ok = WireIo.readVarInt(in) != 0;
            SceneOpError error = SceneOpError.fromId(WireIo.readVarInt(in));
            String message = WireIo.readString(in);
            results.add(new SceneOpResult(targetId, createdId, ok, error, message));
        }
        return new SceneOpAck(batchId, revision, List.copyOf(results));
    }

    public static void writeSceneSnapshot(ByteBuffer out, SceneSnapshot snapshot) {
        WireIo.writeLong(out, snapshot.requestId());
        WireIo.writeLong(out, snapshot.revision());
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        WireIo.writeVarInt(out, nodes.size());
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            WireIo.writeLong(out, node.nodeId());
            WireIo.writeLong(out, node.parentId());
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

    public static SceneSnapshot readSceneSnapshot(ByteBuffer in) {
        long requestId = WireIo.readLong(in);
        long revision = WireIo.readLong(in);
        int nodeCount = WireIo.readVarInt(in);
        if (nodeCount < 0 || nodeCount > 2_000_000) {
            throw new IllegalArgumentException("Invalid node count: " + nodeCount);
        }
        List<SceneSnapshot.NodeSnapshot> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            long nodeId = WireIo.readLong(in);
            long parentId = WireIo.readLong(in);
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

    public static int sceneSaveSize(SceneSave save) {
        return WireIo.stringSize(save.sceneId());
    }

    public static int sceneSaveAckSize(SceneSaveAck ack) {
        return WireIo.stringSize(ack.sceneId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.error());
    }

    public static int sceneCreateSize(SceneCreate create) {
        return WireIo.stringSize(create.sceneId()) + WireIo.stringSize(create.displayName());
    }

    public static int sceneDeleteSize(SceneDelete delete) {
        return WireIo.stringSize(delete.sceneId());
    }

    public static int sceneCreateAckSize(SceneCreateAck ack) {
        return WireIo.stringSize(ack.sceneId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.error());
    }

    public static int sceneDeleteAckSize(SceneDeleteAck ack) {
        return WireIo.stringSize(ack.sceneId())
                + WireIo.varIntSize(ack.success() ? 1 : 0)
                + WireIo.stringSize(ack.error());
    }

    public static int sceneOpBatchSize(SceneOpBatch batch) {
        int size = WireIo.longSize(batch.batchId()) + WireIo.varIntSize(batch.atomic() ? 1 : 0);
        List<SceneOp> ops = batch.ops();
        size += WireIo.varIntSize(ops.size());
        for (SceneOp op : ops) {
            size += WireIo.varIntSize(op.type().id());
            switch (op) {
                case SceneOp.CreateNode createNode ->
                        size += WireIo.longSize(createNode.parentId()) + WireIo.stringSize(createNode.name()) + WireIo.stringSize(createNode.typeId());
                case SceneOp.QueueFree queueFree -> size += WireIo.longSize(queueFree.nodeId());
                case SceneOp.Rename rename -> size += WireIo.longSize(rename.nodeId()) + WireIo.stringSize(rename.newName());
                case SceneOp.SetProperty setProperty ->
                        size += WireIo.longSize(setProperty.nodeId()) + WireIo.stringSize(setProperty.key()) + WireIo.stringSize(setProperty.value());
                case SceneOp.RemoveProperty removeProperty ->
                        size += WireIo.longSize(removeProperty.nodeId()) + WireIo.stringSize(removeProperty.key());
                case SceneOp.Reparent reparent ->
                        size += WireIo.longSize(reparent.nodeId()) + WireIo.longSize(reparent.newParentId()) + WireIo.varIntSize(reparent.index());
            }
        }
        return size;
    }

    public static int sceneOpAckSize(SceneOpAck ack) {
        int size = WireIo.longSize(ack.batchId()) + WireIo.longSize(ack.sceneRevision());
        List<SceneOpResult> results = ack.results();
        size += WireIo.varIntSize(results.size());
        for (SceneOpResult result : results) {
            size += WireIo.longSize(result.targetId()) + WireIo.longSize(result.createdId())
                    + WireIo.varIntSize(result.ok() ? 1 : 0) + WireIo.varIntSize(result.error().id())
                    + WireIo.stringSize(result.message());
        }
        return size;
    }

    public static int sceneSnapshotSize(SceneSnapshot snapshot) {
        int size = WireIo.longSize(snapshot.requestId()) + WireIo.longSize(snapshot.revision());
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        size += WireIo.varIntSize(nodes.size());
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            size += WireIo.longSize(node.nodeId()) + WireIo.longSize(node.parentId())
                    + WireIo.stringSize(node.name()) + WireIo.stringSize(node.type());
            List<SceneSnapshot.Property> props = node.properties();
            size += WireIo.varIntSize(props.size());
            for (SceneSnapshot.Property prop : props) {
                size += WireIo.stringSize(prop.key()) + WireIo.stringSize(prop.value());
            }
        }
        return size;
    }
}
