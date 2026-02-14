package com.moud.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.AssetType;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetManifestRequest;
import com.moud.net.protocol.AssetManifestResponse;
import com.moud.net.protocol.AssetTransferStatus;
import com.moud.net.protocol.AssetUploadAck;
import com.moud.net.protocol.AssetUploadBegin;
import com.moud.net.protocol.AssetUploadChunk;
import com.moud.net.protocol.AssetUploadComplete;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpError;
import com.moud.net.protocol.SceneOpResult;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.wire.WireMessages;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class WireMessagesRoundTripTest {
    @Test
    void sceneOpAck_roundTrips() {
        SceneOpAck ack = new SceneOpAck(
                123L,
                456L,
                List.of(
                        SceneOpResult.created(10L, 11L),
                        SceneOpResult.fail(99L, SceneOpError.NOT_FOUND, "missing")
                )
        );

        Message decoded = WireMessages.decode(WireMessages.encode(ack));
        assertEquals(ack, decoded);
    }

    @Test
    void sceneSnapshotRequest_roundTrips() {
        SceneSnapshotRequest request = new SceneSnapshotRequest(999L);

        Message decoded = WireMessages.decode(WireMessages.encode(request));
        assertEquals(request, decoded);
    }

    @Test
    void sceneSnapshot_roundTrips() {
        SceneSnapshot snapshot = new SceneSnapshot(
                42L,
                7L,
                List.of(
                        new SceneSnapshot.NodeSnapshot(
                                1L,
                                0L,
                                "root",
                                "RootNode",
                                List.of(new SceneSnapshot.Property("foo", "bar"))
                        ),
                        new SceneSnapshot.NodeSnapshot(
                                2L,
                                1L,
                                "child",
                                "PlainNode",
                                List.of()
                        )
                )
        );

        Message decoded = WireMessages.decode(WireMessages.encode(snapshot));
        assertEquals(snapshot, decoded);
    }

    @Test
    void schemaSnapshot_roundTrips() {
        SchemaSnapshot schema = new SchemaSnapshot(1L, List.of(
                new com.moud.core.NodeTypeDef("Node", java.util.Map.of(
                        "foo", new com.moud.core.PropertyDef("foo", com.moud.core.PropertyType.STRING, "bar")
                ))
        ));
        Message decoded = WireMessages.decode(WireMessages.encode(schema));
        assertEquals(schema, decoded);
    }

    @Test
    void assets_roundTrip() {
        ResPath path = new ResPath("res://textures/foo.png");
        AssetHash hash = new AssetHash("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        AssetMeta meta = new AssetMeta(hash, 1234L, AssetType.IMAGE);

        List<Message> messages = List.of(
                new AssetManifestRequest(1L),
                new AssetManifestResponse(2L, List.of(new AssetManifestResponse.Entry(path, meta))),
                new AssetUploadBegin(path, hash, 5L, AssetType.BINARY),
                new AssetUploadAck(path, hash, AssetTransferStatus.OK, "ok"),
                new AssetUploadChunk(hash, 0, new byte[]{1, 2, 3}),
                new AssetUploadComplete(path, hash)
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }
}
