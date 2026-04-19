package com.moud.client.fabric.scripting;

import com.moud.client.fabric.scripting.api.AnimApi;
import com.moud.client.fabric.scripting.api.BodyApi;
import com.moud.client.fabric.scripting.api.CameraApi;
import com.moud.client.fabric.scripting.api.InputApi;
import com.moud.client.fabric.scripting.api.MessagingApi;
import com.moud.client.fabric.scripting.api.MouseApi;
import com.moud.client.fabric.scripting.api.NodeApi;
import com.moud.client.fabric.scripting.api.PlayerStateApi;
import com.moud.client.fabric.scripting.api.PostProcessApi;
import com.moud.client.fabric.scripting.api.RenderApi;
import com.moud.client.fabric.scripting.api.TimerApi;
import com.moud.client.fabric.util.ClientDebugLog;

final class ClientScriptContext implements AutoCloseable {

    private static final String TAG = "ClientScriptContext";

    private final ClientLuauBridge bridge;
    private final Object state;
    private final Object thread;

    final MessagingApi messaging;

    private final int tableRef;
    private final int onReadyRef;
    private final int onFrameRef;
    private final int onDisposeRef;

    private boolean closed;

    ClientScriptContext(
            ClientLuauBridge bridge,
            ClientLuauBridge.Program program,
            BodyApi body,
            InputApi input,
            TimerApi timer,
            AnimApi anim,
            RenderApi render,
            NodeApi node,
            CameraApi camera,
            MouseApi mouse,
            PlayerStateApi playerState,
            PostProcessApi postProcess,
            MessagingApi messaging) {

        this.bridge = bridge;
        this.messaging = messaging;

        ClientLuauBridge.LuauVm vm = bridge.createVm();
        this.state  = vm.state();
        this.thread = vm.thread();

        bridge.setApiGlobal(thread, "body",        body);
        bridge.setApiGlobal(thread, "input",       input);
        bridge.setApiGlobal(thread, "timer",       timer);
        bridge.setApiGlobal(thread, "anim",        anim);
        bridge.setApiGlobal(thread, "render",      render);
        bridge.setApiGlobal(thread, "node",        node);
        bridge.setApiGlobal(thread, "camera",      camera);
        bridge.setApiGlobal(thread, "mouse",       mouse);
        bridge.setApiGlobal(thread, "playerstate", playerState);
        bridge.setApiGlobal(thread, "PostProcess", postProcess);
        bridge.setApiGlobal(thread, "msg",         messaging);
        if (node != null && node.net() != null) {
            bridge.setNestedApiField(thread, "node", "net", node.net());
        }
        bridge.installPrintRedirect(thread);

        bridge.sandboxVm(vm);

        int tRef = bridge.loadAndGetTableRef(thread, program);
        this.tableRef = tRef;

        if (tRef >= 0) {
            onReadyRef   = bridge.getFunctionRef(thread, tRef, "onReady");
            onFrameRef   = bridge.getFunctionRef(thread, tRef, "onFrame");
            onDisposeRef = bridge.getFunctionRef(thread, tRef, "onDispose");

            if (onReadyRef >= 0) {
                bridge.callMethod(thread, tableRef, onReadyRef);
            }
        } else {
            onReadyRef   = -1;
            onFrameRef   = -1;
            onDisposeRef = -1;
        }
    }


    void frame(double dt) {
        if (closed || tableRef < 0 || onFrameRef < 0) return;
        bridge.callMethodWithDt(thread, tableRef, onFrameRef, dt);
    }


    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (tableRef >= 0 && onDisposeRef >= 0) {
            bridge.callMethod(thread, tableRef, onDisposeRef);
        }

        bridge.unref(thread, onReadyRef);
        bridge.unref(thread, onFrameRef);
        bridge.unref(thread, onDisposeRef);
        bridge.unref(thread, tableRef);

        ClientLuauBridge.closeState(state);
    }
}
