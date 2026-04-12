package com.moud.server.minestom.scripting.runtime;


import com.moud.server.minestom.scripting.ScriptCallable;
import com.moud.server.minestom.scripting.ScriptInvocationException;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;
import com.moud.server.minestom.scripting.scene.SceneMutator;
import com.moud.server.minestom.util.DebugLog;

import java.util.ArrayList;
import java.util.Iterator;

public final class TimerTweenScheduler {
    private static final String LOG_TAG = "script-runtime";

    private final ArrayList<PendingTimer> pendingTimers = new ArrayList<>();
    private final ArrayList<PendingTween> pendingTweens = new ArrayList<>();

    public void scheduleTimer(double seconds, ScriptCallable callback) {
        if (callback == null) {
            return;
        }
        pendingTimers.add(new PendingTimer(Math.max(0.0, seconds), callback));
    }

    public void tickTimers(double dtSeconds) {
        if (pendingTimers.isEmpty()) {
            return;
        }
        Iterator<PendingTimer> it = pendingTimers.iterator();
        while (it.hasNext()) {
            PendingTimer t = it.next();
            t.timeLeft -= dtSeconds;
            if (t.timeLeft <= 0.0) {
                it.remove();
                try {
                    t.callback.invoke();
                } catch (ScriptInvocationException e) {
                    DebugLog.error(LOG_TAG, "timer callback error: " + e.getMessage(), e);
                }
            }
        }
    }

    public void tween(SceneMutator mutator, long nodeId, String prop, double fromValue, double targetValue, double duration) {
        if (mutator == null || nodeId <= 0L || prop == null || prop.isBlank()) {
            return;
        }
        if (duration <= 0.0) {
            mutator.queueSet(nodeId, prop, RuntimeScriptUtil.trimFloat((float) targetValue));
            return;
        }
        pendingTweens.removeIf(t -> t.nodeId == nodeId && prop.equals(t.prop));
        pendingTweens.add(new PendingTween(nodeId, prop, (float) fromValue, (float) targetValue, duration));
    }

    public void tickTweens(double dtSeconds, SceneMutator mutator) {
        if (pendingTweens.isEmpty() || mutator == null) {
            return;
        }
        Iterator<PendingTween> it = pendingTweens.iterator();
        while (it.hasNext()) {
            PendingTween t = it.next();
            t.elapsed += dtSeconds;
            float factor = (float) Math.min(1.0, t.elapsed / t.duration);
            float value = t.fromValue + (t.toValue - t.fromValue) * factor;
            mutator.queueSet(t.nodeId, t.prop, RuntimeScriptUtil.trimFloat(value));
            if (factor >= 1.0f) {
                it.remove();
            }
        }
    }

}
