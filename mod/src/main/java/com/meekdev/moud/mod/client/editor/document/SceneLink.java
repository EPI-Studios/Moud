package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.mod.transport.payload.SceneEditPayload;
import com.meekdev.moud.mod.transport.payload.SceneFilePayload;
import com.meekdev.moud.mod.transport.payload.ScenePastePayload;
import com.meekdev.moud.mod.transport.payload.ScenePastedPayload;
import com.meekdev.moud.mod.transport.payload.SceneSavePayload;
import com.meekdev.moud.mod.transport.payload.SceneStatusPayload;
import java.util.ArrayDeque;
import java.util.Queue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SceneLink {

    private static final long MESSAGE_NANOS = 6_000_000_000L;

    private static boolean dirty;
    private static int generation;
    private static String file = "";
    private static String message = "";
    private static long messageAt;
    private static int nextToken = 1;
    private static final Queue<ScenePastedPayload> PASTED = new ArrayDeque<>();

    private SceneLink() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(SceneStatusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ScenePastedPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PASTED.add(payload)));
    }

    private static void receive(SceneStatusPayload status) {
        dirty = status.dirty();
        generation = status.generation();
        file = status.file();
        if (!status.message().isEmpty()) local(status.message());
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

    static ScenePastedPayload takePasted() {
        return PASTED.poll();
    }

    static void send(byte[] changes) {
        if (ClientPlayNetworking.canSend(SceneEditPayload.TYPE)) ClientPlayNetworking.send(new SceneEditPayload(changes));
    }

    static int paste(String text, int parent) {
        int token = nextToken++;
        if (ClientPlayNetworking.canSend(ScenePastePayload.TYPE)) ClientPlayNetworking.send(new ScenePastePayload(token, text, parent));
        return token;
    }

    public static int generation() {
        return generation;
    }

    public static void open(String scene) {
        file(SceneFilePayload.OPEN, scene);
    }

    public static void saveAs(String scene) {
        file(SceneFilePayload.SAVE_AS, scene);
    }

    public static void restore(String backup) {
        file(SceneFilePayload.RESTORE, backup);
    }

    private static void file(int action, String path) {
        if (ClientPlayNetworking.canSend(SceneFilePayload.TYPE)) ClientPlayNetworking.send(new SceneFilePayload(action, path));
    }

    static void save() {
        if (ClientPlayNetworking.canSend(SceneSavePayload.TYPE)) ClientPlayNetworking.send(new SceneSavePayload());
    }
}
