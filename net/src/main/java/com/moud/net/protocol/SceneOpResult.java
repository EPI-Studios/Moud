package com.moud.net.protocol;

public record SceneOpResult(
        long targetId,
        long createdId,
        boolean ok,
        SceneOpError error,
        String message
) {
    public static SceneOpResult ok(long targetId) {
        return new SceneOpResult(targetId, 0L, true, SceneOpError.NONE, "");
    }

    public static SceneOpResult created(long parentId, long createdId) {
        return new SceneOpResult(parentId, createdId, true, SceneOpError.NONE, "");
    }

    public static SceneOpResult fail(long targetId, SceneOpError error, String message) {
        return new SceneOpResult(targetId, 0L, false, error, message);
    }
}
