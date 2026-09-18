package com.meekdev.moud.core.image;

import java.util.HashMap;
import java.util.Map;

public final class ImageStore {

    public static final String SCHEME = "editable://";

    private static final Map<Integer, EditableImage> IMAGES = new HashMap<>();
    private static int next = 1;

    private ImageStore() {}

    public static synchronized int add(EditableImage image) {
        int id = next++;
        IMAGES.put(id, image);
        return id;
    }

    public static synchronized void remove(int id) {
        IMAGES.remove(id);
    }

    public static String uri(int id) {
        return SCHEME + id;
    }

    public static boolean isEditable(String source) {
        return source.startsWith(SCHEME);
    }

    public static synchronized EditableImage find(String source) {
        if (!isEditable(source)) return null;
        try {
            return IMAGES.get(Integer.parseInt(source.substring(SCHEME.length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static synchronized EditableImage find(int id) {
        return IMAGES.get(id);
    }
}
