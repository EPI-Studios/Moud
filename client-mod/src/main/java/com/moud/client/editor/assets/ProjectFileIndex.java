package com.moud.client.editor.assets;

import com.moud.client.network.ClientPacketWrapper;
import com.moud.network.MoudPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the asset/src directory tree provided by the server so pickers and drag/drop
 * can query the full project layout locally.
 */
public final class ProjectFileIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger("ProjectFileIndex");
    private static final ProjectFileIndex INSTANCE = new ProjectFileIndex();

    private final NavigableMap<String, Node> roots = new TreeMap<>();
    private final AtomicBoolean requested = new AtomicBoolean();

    private ProjectFileIndex() {}

    public static ProjectFileIndex getInstance() {
        return INSTANCE;
    }

    public synchronized void clear() {
        roots.clear();
    }

    public synchronized boolean isEmpty() {
        return roots.isEmpty();
    }

    public synchronized List<Node> getRoots() {
        return List.copyOf(roots.values());
    }

    public synchronized List<Node> listFiles() {
        List<Node> files = new ArrayList<>();
        for (Node root : roots.values()) {
            collectFiles(root, files);
        }
        return files;
    }

    public void handleProjectMap(MoudPackets.ProjectMapPacket packet) {
        if (packet == null) {
            return;
        }
        applySnapshot(packet.entries());
    }

    public void requestSyncIfNeeded() {
        if (requested.compareAndSet(false, true)) {
            ClientPacketWrapper.sendToServer(new MoudPackets.RequestProjectMapPacket());
        }
    }

    public void forceRefresh() {
        requested.set(false);
        requestSyncIfNeeded();
    }

    /**
     * Applies a snapshot provided by the server. Entries should contain normalized (/) paths.
     */
    public synchronized void applySnapshot(List<MoudPackets.ProjectFileEntry> entries) {
        roots.clear();
        if (entries == null || entries.isEmpty()) {
            LOGGER.info("Project file index cleared (no entries)");
            return;
        }
        for (MoudPackets.ProjectFileEntry entry : entries) {
            if (entry == null || entry.path() == null || entry.path().isBlank()) {
                continue;
            }
            String normalized = entry.path().replace('\\', '/');
            String[] parts = normalized.split("/");
            EntryKind leafKind = toEntryKind(entry.kind());
            Node current = ensureRoot(parts[0], leafKind == EntryKind.FILE && parts.length == 1 ? EntryKind.FILE : EntryKind.DIRECTORY);
            StringBuilder pathBuilder = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                pathBuilder.append('/').append(parts[i]);
                boolean last = i == parts.length - 1;
                EntryKind kind = last ? leafKind : EntryKind.DIRECTORY;
                current = current.ensureChild(parts[i], pathBuilder.toString(), kind);
            }
        }
        LOGGER.info("Project file index loaded {} entries", entries.size());
    }

    private Node ensureRoot(String name, EntryKind kind) {
        Node node = roots.get(name);
        if (node == null) {
            node = new Node(name, name, kind);
            roots.put(name, node);
        } else {
            node.promoteTo(kind);
        }
        return node;
    }

    private EntryKind toEntryKind(MoudPackets.ProjectEntryKind kind) {
        if (kind == null) {
            return EntryKind.FILE;
        }
        return switch (kind) {
            case DIRECTORY -> EntryKind.DIRECTORY;
            case FILE -> EntryKind.FILE;
        };
    }

    public enum EntryKind {
        DIRECTORY,
        FILE
    }

    public static final class Node {
        private final String name;
        private final String path;
        private EntryKind kind;
        private final NavigableMap<String, Node> children = new TreeMap<>();

        private Node(String name, String path, EntryKind kind) {
            this.name = name;
            this.path = path;
            this.kind = kind;
        }

        public String name() {
            return name;
        }

        public String path() {
            return path;
        }

        public EntryKind kind() {
            return kind;
        }

        public boolean isDirectory() {
            return kind == EntryKind.DIRECTORY;
        }

        public List<Node> children() {
            return List.copyOf(children.values());
        }

        private Node ensureChild(String childName, String childPath, EntryKind requestedKind) {
            Node node = children.get(childName);
            if (node == null) {
                node = new Node(childName, childPath, requestedKind);
                children.put(childName, node);
            } else {
                node.promoteTo(requestedKind);
            }
            return node;
        }

        private void promoteTo(EntryKind requestedKind) {
            if (requestedKind == EntryKind.DIRECTORY && kind != EntryKind.DIRECTORY) {
                kind = EntryKind.DIRECTORY;
            }
        }
    }

    private void collectFiles(Node node, List<Node> out) {
        if (node.isDirectory()) {
            for (Node child : node.children()) {
                collectFiles(child, out);
            }
        } else {
            out.add(node);
        }
    }
}
