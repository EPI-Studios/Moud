package com.moud.client.fabric.scripting;

import com.moud.client.fabric.util.ClientDebugLog;
import java.util.Map;

public final class LuauCallback {
    private final LuauRuntime runtime;
    private final Object thread;
    private int funcRef;

    LuauCallback(LuauRuntime runtime, Object thread, int funcRef) {
        this.runtime = runtime;
        this.thread = thread;
        this.funcRef = funcRef;
    }

    public void invoke(Object... args) {
        if (funcRef < 0) return;
        try {
            runtime.getRef(thread, funcRef);
            for (Object arg : args) pushValue(arg);
            runtime.call(thread, args.length, 0);
        } catch (Exception e) {
            ClientDebugLog.error("LuauCallback", "invoke failed: " + e.getMessage(), e);
        } finally {
            runtime.top(thread, 0);
        }
    }

    public void release() {
        if (funcRef >= 0) {
            try { runtime.unref(thread, funcRef); } catch (Exception e) {
                ClientDebugLog.error("LuauCallback", "unref failed: " + e.getMessage(), e);
            }
            funcRef = -1;
        }
    }

    private void pushValue(Object v) {
        if (v == null) { runtime.pushNil(thread); return; }
        if (v instanceof Boolean b) { runtime.pushBoolean(thread, b); return; }
        if (v instanceof Number n)  { runtime.pushNumber(thread, n.doubleValue()); return; }
        if (v instanceof String s)  { runtime.pushString(thread, s); return; }
        if (v instanceof byte[] b)  { runtime.pushString(thread, new String(b)); return; }
        if (v instanceof Map<?, ?> m) {
            runtime.newTable(thread);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pushValue(e.getValue());
                runtime.setField(thread, -2, String.valueOf(e.getKey()));
            }
            return;
        }
        runtime.pushNil(thread);
    }
}
