package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.ChatInputBar;
import com.meekdev.moud.core.instance.ChatTabs;
import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.List;
import org.jspecify.annotations.Nullable;

// the chat's configuration instances the place put in the tree. the first of each is the one used
public final class ChatLook {

    private static final ChatWindow DEFAULT_WINDOW = new ChatWindow();
    private static final ChatInputBar DEFAULT_BAR = new ChatInputBar();

    private ChatLook() {}

    public static @Nullable ChatWindow window() {
        return first(Classes.CHAT_WINDOW);
    }

    // the window to draw with: the place's, or one with every default
    public static ChatWindow windowOrDefault() {
        ChatWindow window = window();
        return window == null ? DEFAULT_WINDOW : window;
    }

    public static @Nullable ChatInputBar inputBar() {
        return first(Classes.CHAT_INPUT_BAR);
    }

    public static ChatInputBar inputBarOrDefault() {
        ChatInputBar bar = inputBar();
        return bar == null ? DEFAULT_BAR : bar;
    }

    public static @Nullable ChatTabs tabs() {
        return first(Classes.CHAT_TABS);
    }

    private static <T extends Instance> @Nullable T first(ClassDef<T> def) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return null;
        List<T> all = tree.ofClass(def);
        return all.isEmpty() ? null : all.getFirst();
    }
}
