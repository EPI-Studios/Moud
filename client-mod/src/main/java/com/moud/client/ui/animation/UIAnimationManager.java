package com.moud.client.ui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UIAnimationManager {
    private static final UIAnimationManager INSTANCE = new UIAnimationManager();
    private final List<UIAnimation> activeAnimations = new CopyOnWriteArrayList<>();
    private volatile boolean debugEnabled;
    private volatile long lastDebugNanos;

    private UIAnimationManager() {}

    public static UIAnimationManager getInstance() {
        return INSTANCE;
    }

    public void addAnimation(UIAnimation animation) {
        activeAnimations.add(animation);
        debug("add", activeAnimations.size());
    }

    public void removeAnimation(UIAnimation animation) {
        activeAnimations.remove(animation);
        debug("remove", activeAnimations.size());
    }

    public void update() {
        List<UIAnimation> toRemove = new ArrayList<>();
        for (UIAnimation anim : activeAnimations) {
            if (!anim.update()) {
                toRemove.add(anim);
            }
        }
        activeAnimations.removeAll(toRemove);
        if (!toRemove.isEmpty()) {
            debug("completed " + toRemove.size(), activeAnimations.size());
        }
    }

    public void clear() {
        activeAnimations.clear();
        debug("clear", 0);
    }

    public int getActiveCount() {
        return activeAnimations.size();
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        debug("toggle", activeAnimations.size());
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    private void debug(String action, int size) {
        if (!debugEnabled) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastDebugNanos < 50_000_000L && !"toggle".equals(action)) { // ~20fps cap
            return;
        }
        lastDebugNanos = now;
        System.out.println("[UIAnimation] action=" + action + " active=" + size);
    }
}
