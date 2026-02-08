package com.moud.server.entity;

import com.moud.server.proxy.MediaDisplayProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class DisplayManager {
    private static final DisplayManager INSTANCE = new DisplayManager();

    private final AtomicLong idCounter = new AtomicLong();
    private final Map<Long, MediaDisplayProxy> displays = new ConcurrentHashMap<>();
    private Task anchorUpdateTask;

    private DisplayManager() {
    }

    public static DisplayManager getInstance() {
        return INSTANCE;
    }

    public long nextId() {
        return idCounter.incrementAndGet();
    }

    public void register(MediaDisplayProxy display) {
        displays.put(display.getId(), display);
        ensureUpdateTask();
    }

    public void unregister(MediaDisplayProxy display) {
        displays.remove(display.getId());
        if (displays.isEmpty() && anchorUpdateTask != null) {
            anchorUpdateTask.cancel();
            anchorUpdateTask = null;
        }
    }

    public MediaDisplayProxy getById(long id) {
        return displays.get(id);
    }

    public Collection<MediaDisplayProxy> getAllDisplays() {
        return displays.values();
    }

    public void clear() {
        displays.values().forEach(MediaDisplayProxy::removeSilently);
        displays.clear();
        if (anchorUpdateTask != null) {
            anchorUpdateTask.cancel();
            anchorUpdateTask = null;
        }
    }

    private void ensureUpdateTask() {
        if (anchorUpdateTask != null) {
            return;
        }
        anchorUpdateTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::tickAnchoredDisplays)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    private void tickAnchoredDisplays() {
        if (displays.isEmpty()) {
            if (anchorUpdateTask != null) {
                anchorUpdateTask.cancel();
                anchorUpdateTask = null;
            }
            return;
        }

        displays.values().forEach(MediaDisplayProxy::updateAnchorTracking);
    }
}
