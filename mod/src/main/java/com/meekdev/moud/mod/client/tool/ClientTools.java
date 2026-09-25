package com.meekdev.moud.mod.client.tool;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Tool;
import com.meekdev.moud.core.character.Tools;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.transport.payload.ToolPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ClientTools {

    private static final Map<Tool, Boolean> HELD = new WeakHashMap<>();
    private static boolean pressed;
    private static @Nullable Tool active;

    private static boolean holding;

    private ClientTools() {}

    public static boolean holding() {
        return holding;
    }

    public static void idle() {
        holding = false;
        release();
    }

    public static void tick(InstanceTree tree, @Nullable Character own) {
        for (Tool tool : tree.ofClass(Classes.TOOL)) {
            boolean held = tool.parent() instanceof Character;
            Boolean was = HELD.put(tool, held);
            if (was == null || was == held) continue;
            if (held) tool.equipped.fire(tool);
            else tool.unequipped.fire(tool);
        }
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character body) Tools.hold(body, false);
        }
        Minecraft client = Minecraft.getInstance();
        boolean down = client.screen == null && client.options.keyAttack.isDown();
        Tool tool = own == null ? null : Tools.held(own);
        holding = tool != null;
        if (active != tool) active = null;
        if (down == pressed) return;
        pressed = down;
        if (!down) {
            release();
            return;
        }
        if (tool == null || tool.manualActivationOnly) return;
        active = tool;
        Tools.activate(tool, true);
        send(true);
    }

    private static void release() {
        Tool tool = active;
        if (tool == null) return;
        active = null;
        if (tool.isAlive()) Tools.activate(tool, false);
        send(false);
    }

    private static void send(boolean down) {
        if (ClientPlayNetworking.canSend(ToolPayload.TYPE)) ClientPlayNetworking.send(new ToolPayload(down));
    }
}
