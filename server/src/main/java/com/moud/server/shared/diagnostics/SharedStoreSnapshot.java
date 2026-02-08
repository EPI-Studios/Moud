package com.moud.server.shared.diagnostics;

import java.util.Map;

public record SharedStoreSnapshot(
        String playerId,
        String storeName,
        int totalKeys,
        int changeListenerCount,
        int keyListenerCount,
        Map<String, SharedValueSnapshot> values
) {
}

