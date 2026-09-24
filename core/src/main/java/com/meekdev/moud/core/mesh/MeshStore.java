package com.meekdev.moud.core.mesh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MeshStore {

    public static final String SCHEME = "editablemesh://";

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final MeshStore SERVER = new MeshStore();
    private static final MeshStore CLIENT = new MeshStore();

    private final Map<Integer, EditableMesh> meshes = new ConcurrentHashMap<>();

    private MeshStore() {}

    public static MeshStore server() {
        return SERVER;
    }

    public static MeshStore client() {
        return CLIENT;
    }

    public static MeshStore of(boolean client) {
        return client ? CLIENT : SERVER;
    }

    public int add(EditableMesh mesh) {
        int id = NEXT.getAndIncrement();
        meshes.put(id, mesh);
        return id;
    }

    public void remove(int id) {
        meshes.remove(id);
    }

    public static String uri(int id) {
        return SCHEME + id;
    }

    public static boolean isEditable(String source) {
        return source.startsWith(SCHEME);
    }

    public EditableMesh find(String source) {
        if (!isEditable(source)) return null;
        try {
            return meshes.get(Integer.parseInt(source.substring(SCHEME.length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public EditableMesh find(int id) {
        return meshes.get(id);
    }
}
