package com.meekdev.moud.mod.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Budget {

    private final int most;
    private final Map<UUID, Integer> spent = new HashMap<>();

    public Budget(int most) {
        this.most = most;
    }

    public synchronized boolean take(UUID player) {
        return spent.merge(player, 1, Integer::sum) <= most;
    }

    public synchronized void refill() {
        spent.clear();
    }
}
