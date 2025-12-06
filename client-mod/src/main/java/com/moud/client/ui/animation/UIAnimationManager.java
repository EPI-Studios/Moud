package com.moud.client.ui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UIAnimationManager {
    private static final UIAnimationManager INSTANCE = new UIAnimationManager();
    private final List<UIAnimation> activeAnimations = new CopyOnWriteArrayList<>();

    private UIAnimationManager() {}

    public static UIAnimationManager getInstance() {
        return INSTANCE;
    }

    public void addAnimation(UIAnimation animation) {
        activeAnimations.add(animation);
    }

    public void removeAnimation(UIAnimation animation) {
        activeAnimations.remove(animation);
    }

    public void update() {
        List<UIAnimation> toRemove = new ArrayList<>();
        for (UIAnimation anim : activeAnimations) {
            if (!anim.update()) {
                toRemove.add(anim);
            }
        }
        activeAnimations.removeAll(toRemove);
    }

    public void clear() {
        activeAnimations.clear();
    }

    public int getActiveCount() {
        return activeAnimations.size();
    }
}
