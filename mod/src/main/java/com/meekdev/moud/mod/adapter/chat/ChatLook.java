package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.List;
import org.jspecify.annotations.Nullable;

// the chat window a place asked for, or null for the game's own as the player set it up
public final class ChatLook {

    private ChatLook() {}

    public static @Nullable ChatWindow window() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return null;
        List<ChatWindow> windows = tree.ofClass(Classes.CHAT_WINDOW);
        return windows.isEmpty() ? null : windows.getFirst();
    }
}
