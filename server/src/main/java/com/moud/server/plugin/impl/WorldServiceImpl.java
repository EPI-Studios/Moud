package com.moud.server.plugin.impl;

import com.moud.plugin.api.services.WorldService;
import com.moud.server.instance.InstanceManager;
import net.minestom.server.instance.Instance;

public final class WorldServiceImpl implements WorldService {
    private Instance target() {
        return InstanceManager.getInstance().getDefaultInstance();
    }

    @Override
    public long getTime() {
        return target().getTime();
    }

    @Override
    public void setTime(long time) {
        target().setTime(time);
    }

    @Override
    public int getTimeRate() {
        return target().getTimeRate();
    }

    @Override
    public void setTimeRate(int timeRate) {
        if (timeRate < 0) {
            throw new IllegalArgumentException("timeRate must be non-negative");
        }
        target().setTimeRate(timeRate);
    }

    @Override
    public int getTimeSynchronizationTicks() {
        return target().getTimeSynchronizationTicks();
    }

    @Override
    public void setTimeSynchronizationTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        target().setTimeSynchronizationTicks(ticks);
    }
}
