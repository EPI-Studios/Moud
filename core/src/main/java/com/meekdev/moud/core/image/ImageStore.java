package com.meekdev.moud.core.image;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImageStore {

    public static final String SCHEME = "editable://";

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final ImageStore SERVER = new ImageStore();
    private static final ImageStore CLIENT = new ImageStore();

    private final Map<Integer, EditableImage> images = new ConcurrentHashMap<>();

    private ImageStore() {}

    public static ImageStore server() {
        return SERVER;
    }

    public static ImageStore client() {
        return CLIENT;
    }

    public static ImageStore of(boolean client) {
        return client ? CLIENT : SERVER;
    }

    public int add(EditableImage image) {
        int id = NEXT.getAndIncrement();
        images.put(id, image);
        return id;
    }

    public void remove(int id) {
        images.remove(id);
    }

    public static String uri(int id) {
        return SCHEME + id;
    }

    public static boolean isEditable(String source) {
        return source.startsWith(SCHEME);
    }

    public EditableImage find(String source) {
        if (!isEditable(source)) return null;
        try {
            return images.get(Integer.parseInt(source.substring(SCHEME.length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public EditableImage find(int id) {
        return images.get(id);
    }
}
