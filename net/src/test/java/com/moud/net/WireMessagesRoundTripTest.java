package com.moud.net;

import static Assertions.assertEquals;

import com.moud.core.PropertyType;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

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
                new NodeTypeDef("Node", Map.of(
                        "foo", new PropertyDef("foo", STRING, "bar")
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

    @Test
    void runtime_roundTrip() {
        List<Message> messages = List.of(
                new PlayerInput(123L, 1.0f, -0.25f, 90.0f, -10.0f, 0.25f, -0.75f, true, false),
                new RuntimeState(456L, "main",
                        true, 0.1f, 0.2f, 0.3f, 0.0125f,
                        12345, "thunder", 0.75f,
                        true, 10.0f, 20.0f, 30.0f, 45.0f, -15.0f, 22.0f,
                        false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                        false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                new RuntimeState(789L, "test",
                        false, 0.5f, 0.5f, 0.5f, 0.02f,
                        6000, "clear", 1.0f,
                        false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                        true, 0.0f, 3.0f, -5.0f, -15.0f, 0.0f,
                        false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }

    @Test
    void runtime_playerInput_legacyDecode() {
        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(64);
        com.moud.net.wire.WireIo.writeVarInt(out, MessageType.PLAYER_INPUT.id());
        com.moud.net.wire.WireIo.writeLong(out, 123L);
        out.putFloat(1.0f);
        out.putFloat(-0.25f);
        out.putFloat(90.0f);
        out.putFloat(-10.0f);
        com.moud.net.wire.WireIo.writeVarInt(out, 1);
        out.flip();

        byte[] bytes = new byte[out.remaining()];
        out.get(bytes);

        Message decoded = WireMessages.decode(bytes);
        assertEquals(new PlayerInput(123L, 1.0f, -0.25f, 90.0f, -10.0f, 0.0f, 0.0f, true, false), decoded);
    }

    @Test
    void sceneSave_roundTrip() {
        List<Message> messages = List.of(
                new SceneSave("main"),
                new SceneSaveAck("main", true, null),
                new SceneSaveAck("sandbox", false, "disk full")
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }

    @Test
    void sceneCreateDelete_roundTrip() {
        List<Message> messages = List.of(
                new SceneCreate("level1", "Level 1"),
                new SceneDelete("level1"),
                new SceneCreateAck("level1", true, null),
                new SceneDeleteAck("level1", false, "scene in use")
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }

    @Test
    void control_roundTrip() {
        List<Message> messages = List.of(
                new Hello(3),
                new ServerHello(3, false),
                new ServerHello(3, true)
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }

    @Test
    void project_roundTrip() {
        List<Message> messages = List.of(
                new ProjectInfoRequest(1L),
                new ProjectInfo(1L, false, "", ""),
                new ProjectInfo(2L, true, "My Game", "Meek"),
                new ProjectCreate(3L, "My Game", "Meek"),
                new ProjectCreateAck(3L, true, null, "My Game", "Meek"),
                new ProjectCreateAck(4L, false, "already exists", "", "")
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }

    @Test
    void scriptActions_roundTrip() {
        List<Message> messages = List.of(
                new ScriptActionListRequest(1L, 123L),
                new ScriptActionListResponse(1L, 123L, true, null, List.of("Bake", "Spawn")),
                new ScriptActionListResponse(2L, 123L, false, "no script", List.of()),
                new ScriptActionInvoke(3L, 123L, "Bake"),
                new ScriptActionInvokeAck(3L, 123L, true, null),
                new ScriptActionInvokeAck(4L, 123L, false, "error")
        );

        for (Message message : messages) {
            Message decoded = WireMessages.decode(WireMessages.encode(message));
            assertEquals(message, decoded);
        }
    }
}
