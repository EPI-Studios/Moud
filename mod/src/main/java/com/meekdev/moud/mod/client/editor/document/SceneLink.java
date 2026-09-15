package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.mod.transport.payload.SceneEditPayload;
import com.meekdev.moud.mod.transport.payload.SceneInsertPayload;
import com.meekdev.moud.mod.transport.payload.SceneSavePayload;
import com.meekdev.moud.mod.transport.payload.SceneStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SceneLink {

    private static final long MESSAGE_NANOS = 6_000_000_000L;

    private static boolean dirty;
    private static String file = "";
    private static String message = "";
    private static long messageAt;
    private static int inserted;

    private SceneLink() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(SceneStatusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
    }

    private static void receive(SceneStatusPayload status) {
        dirty = status.dirty();
        file = status.file();
        if (!status.message().isEmpty()) {
            message = status.message();
            messageAt = System.nanoTime();
        }
        if (status.inserted() != 0) inserted = status.inserted();
    }

    static void local(String why) {
        message = why;
        messageAt = System.nanoTime();
    }

    public static boolean dirty() {
        return dirty;
    }

    public static String file() {
        return file;
    }

    public static String message() {
        return System.nanoTime() - messageAt < MESSAGE_NANOS ? message : "";
    }

    static int takeInserted() {
        int id = inserted;
        inserted = 0;
        return id;
    }

    static void send(byte[] changes) {
        if (ClientPlayNetworking.canSend(SceneEditPayload.TYPE)) ClientPlayNetworking.send(new SceneEditPayload(changes));
    }

    static void insert(String className, int parent) {
        if (ClientPlayNetworking.canSend(SceneInsertPayload.TYPE)) ClientPlayNetworking.send(new SceneInsertPayload(className, parent));
    }

    static void save() {
        if (ClientPlayNetworking.canSend(SceneSavePayload.TYPE)) ClientPlayNetworking.send(new SceneSavePayload());
    }
}
