package com.moud.client.editor.assets;

import com.moud.api.util.PathUtils;
import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectFileContentCache {
    private static final ProjectFileContentCache INSTANCE = new ProjectFileContentCache();

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private ProjectFileContentCache() {}

    public static ProjectFileContentCache getInstance() {
        return INSTANCE;
    }

    public Entry get(String path) {
        if (path == null) {
            return null;
        }
        return entries.get(normalize(path));
    }

    public void request(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String normalized = normalize(path);
        entries.computeIfAbsent(normalized, p -> new Entry(normalized)).markLoading();
        ClientPacketWrapper.sendToServer(new MoudPackets.RequestProjectFilePacket(normalized));
    }

    public void handleContent(MoudPackets.ProjectFileContentPacket packet) {
        if (packet == null || packet.path() == null) {
            return;
        }
        String normalized = normalize(packet.path());
        Entry entry = entries.computeIfAbsent(normalized, p -> new Entry(normalized));
        entry.update(packet);
    }

    private String normalize(String path) {
        return PathUtils.normalizeSlashes(path);
    }

    public static final class Entry {
        private final String path;
        private volatile boolean loading = true;
        private volatile boolean success;
        private volatile String content;
        private volatile String message;
        private volatile String absolutePath;

        private Entry(String path) {
            this.path = path;
        }

        private void markLoading() {
            this.loading = true;
        }

        private void update(MoudPackets.ProjectFileContentPacket packet) {
            this.loading = false;
            this.success = packet.success();
            this.content = packet.content();
            this.message = packet.message();
            this.absolutePath = packet.absolutePath();
        }

        public String path() {
            return path;
        }

        public boolean loading() {
            return loading;
        }

        public boolean success() {
            return success;
        }

        public String content() {
            return content;
        }

        public String message() {
            return message;
        }

        public String absolutePath() {
            return absolutePath;
        }
    }
}
