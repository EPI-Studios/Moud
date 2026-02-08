package com.moud.server.shared.diagnostics;

import com.moud.server.shared.core.SharedValue;

public record SharedValueSnapshot(
        String key,
        Object value,
        SharedValue.Permission permission,
        SharedValue.SyncMode syncMode,
        long lastModified,
        boolean dirty,
        SharedValue.Writer lastWriter,
        String lastWriterId
) {
}

