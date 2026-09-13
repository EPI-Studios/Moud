package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.ChatInputBar;
import com.meekdev.moud.core.instance.ChatWindow;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.TextChannel;
import com.meekdev.moud.core.instance.TextSource;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.transport.Packets;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.engine.ScriptEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class ClientChat implements ChatRef {

    public static final ClientChat INSTANCE = new ClientChat();

    public static final class Shown {
        public final ChatLine line;
        public Map<String, Object> look = Map.of();
        public List<RichText.Piece> pieces = List.of();
        public String composed = "";
        public final long added = System.nanoTime();
        public long removed = -1;
        public int layoutKey;
        public Object layout;

        Shown(ChatLine line) {
            this.line = line;
        }
    }

    private final Queue<Packets.ChatDown> incoming = new ConcurrentLinkedQueue<>();
    private final List<Shown> lines = new ArrayList<>();
    private final Map<Integer, Integer> unread = new HashMap<>();
    private Instance target;
    private long nextLocal = -1;
    private long lastTyping;

    private ClientChat() {}

    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(Packets.ChatDown.TYPE, (payload, context) -> INSTANCE.incoming.add(payload));
    }

    public List<Shown> lines() {
        return lines;
    }

    public int unread(TextChannel channel) {
        return unread.getOrDefault(channel.id(), 0);
    }

    public void tick() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) {
            incoming.clear();
            lines.clear();
            Bubbles.clear();
            return;
        }
        for (Packets.ChatDown down; (down = incoming.poll()) != null; ) {
            switch (down.kind()) {
                case Packets.ChatDown.LINE -> add(ChatLine.of(down));
                case Packets.ChatDown.EDIT -> edited(ChatLine.of(down));
                case Packets.ChatDown.DELETE -> remove(down.id());
                case Packets.ChatDown.STATUS -> refused(ChatLine.of(down));
                case Packets.ChatDown.CLEAR -> clearLocal();
                default -> {}
            }
        }
        if (target != null && (!target.isAlive() || target.tree() != tree)) target = null;
        long now = System.nanoTime();
        lines.removeIf(one -> one.removed >= 0 && now - one.removed > 2_000_000_000L);
    }

    public static boolean active() {
        return ClientScene.tree() != null;
    }

    private static ScriptEngine vm() {
        return ClientPlace.vm();
    }

    public void add(ChatLine line) {
        InstanceTree tree = ClientScene.tree();
        Shown shown = new Shown(line);
        style(shown, tree);
        lines.add(shown);
        ChatWindow window = ChatLook.window();
        int max = window == null ? 100 : window.maxMessages;
        while (lines.size() > max) lines.removeFirst();
        if (line.channel >= 0 && (target == null || target.id() != line.channel)) unread.merge(line.channel, 1, Integer::sum);
        Map<String, Object> message = line.toMap(tree);
        bubbleFor(line, message, tree);
        if (tree != null && tree.byId(line.channel) instanceof TextChannel channel) channel.messageReceived.fire(message);
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("messageReceived", message);
    }

    @SuppressWarnings("unchecked")
    private void bubbleFor(ChatLine line, Map<String, Object> message, InstanceTree tree) {
        if (tree == null || line.body < 0 || !"Success".equals(line.status)) return;
        Instance body = tree.byId(line.body);
        if (body == null) return;
        Map<String, Object> look = Map.of();
        ScriptEngine vm = vm();
        if (vm != null) {
            Object[] out = vm.chatHook("onBubble", message);
            if (out != null && out.length > 0) {
                if (Boolean.FALSE.equals(out[0])) return;
                if (out[0] instanceof Map<?, ?> map) look = (Map<String, Object>) map;
            }
        }
        String text = look.get("text") instanceof String changed ? changed : line.text;
        Bubbles.add(body, text, look);
    }

    private void style(Shown shown, InstanceTree tree) {
        Map<String, Object> look = new HashMap<>();
        Map<String, Object> message = shown.line.toMap(tree);
        if (tree != null && tree.byId(shown.line.channel) instanceof TextChannel channel) {
            merge(look, channel.onIncoming.first(null, message));
        }
        ScriptEngine vm = vm();
        if (vm != null) {
            Object[] out = vm.chatHook("onIncoming", message);
            if (out != null && out.length > 0) merge(look, out[0]);
        }
        shown.look = look;
        shown.layout = null;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> into, Object from) {
        if (from instanceof Map<?, ?> map) into.putAll((Map<String, Object>) map);
    }

    private void edited(ChatLine changed) {
        for (Shown shown : lines) {
            if (shown.line.id != changed.id) continue;
            shown.line.text = changed.text;
            shown.line.prefix = changed.prefix;
            shown.line.metadata = changed.metadata;
            style(shown, ClientScene.tree());
            ScriptEngine vm = vm();
            if (vm != null) vm.chatEvent("edited", shown.line.toMap(ClientScene.tree()));
        }
    }

    private void remove(long id) {
        for (Shown shown : lines) {
            if (shown.line.id == id && shown.removed < 0) shown.removed = System.nanoTime();
        }
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("deleted", (double) id);
    }

    private void refused(ChatLine line) {
        ScriptEngine vm = vm();
        Map<String, Object> message = line.toMap(ClientScene.tree());
        if (vm != null) vm.chatEvent("sending", message);
        String why = switch (line.status) {
            case "Muted" -> "You can't send messages in this channel.";
            case "TooLong" -> "That message is too long.";
            case "SlowMode" -> "Slow down, this channel has slow mode on.";
            case "Floodchecked" -> "You're sending messages too quickly.";
            case "NotInChannel" -> "You're not in that channel.";
            case "Empty" -> "";
            default -> "Your message wasn't sent.";
        };
        if (why.isEmpty()) return;
        ChatLine notice = new ChatLine(nextLocal--);
        notice.text = "<color=#ff6b6b>" + RichText.escape(why) + "</color>";
        notice.status = line.status;
        notice.timestamp = System.currentTimeMillis();
        add(notice);
    }

    public void vanilla(Component component) {
        ChatLine line = new ChatLine(nextLocal--);
        line.text = ChatText.markup(component);
        line.status = "System";
        line.timestamp = System.currentTimeMillis();
        add(line);
    }

    public void typed(String text) {
        Instance channel = target();
        LocalPlayer me = Minecraft.getInstance().player;
        ChatLine line = new ChatLine(0);
        line.channel = channel == null ? -1 : channel.id();
        line.text = RichText.escape(text);
        line.prefix = me == null ? "" : RichText.escape(me.getGameProfile().name());
        line.status = "Sending";
        line.timestamp = System.currentTimeMillis();
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("sending", line.toMap(ClientScene.tree()));
        ClientPlayNetworking.send(new Packets.ChatUp(line.channel, text));
    }

    public void typing(String text) {
        long now = System.currentTimeMillis();
        if (now - lastTyping < 100) return;
        lastTyping = now;
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("typing", text);
    }

    public void opened() {
        if (target != null) unread.remove(target.id());
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("opened");
    }

    public void closed() {
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("closed");
    }

    public List<TextChannel> channels() {
        List<TextChannel> out = new ArrayList<>();
        InstanceTree tree = ClientScene.tree();
        LocalPlayer me = Minecraft.getInstance().player;
        if (tree == null || me == null) return out;
        String id = me.getUUID().toString();
        for (TextChannel channel : tree.ofClass(Classes.TEXT_CHANNEL)) {
            for (Instance child : channel.children()) {
                if (child instanceof TextSource source && source.player.equals(id)) {
                    out.add(channel);
                    break;
                }
            }
        }
        return out;
    }

    private void clearLocal() {
        lines.clear();
        unread.clear();
    }

    @Override
    public long send(Map<String, Object> message) {
        if (Boolean.TRUE.equals(message.get("system"))) {
            ChatLine line = new ChatLine(nextLocal--);
            line.text = String.valueOf(message.getOrDefault("text", ""));
            line.prefix = message.get("prefix") instanceof String prefix ? prefix : "";
            line.metadata = message.get("metadata") instanceof String metadata ? metadata : "";
            line.channel = message.get("channel") instanceof TextChannel channel ? channel.id() : -1;
            line.status = "System";
            line.timestamp = System.currentTimeMillis();
            add(line);
            return line.id;
        }
        if (message.get("channel") instanceof TextChannel channel) target = channel;
        typed(String.valueOf(message.getOrDefault("text", "")));
        return 0;
    }

    @Override
    public void edit(long id, Map<String, Object> changes) {
        for (Shown shown : lines) {
            if (shown.line.id != id) continue;
            shown.line.apply(changes);
            style(shown, ClientScene.tree());
            return;
        }
        throw new IllegalArgumentException("there is no message " + id + " in this client's chat");
    }

    @Override
    public void delete(long id) {
        remove(id);
    }

    @Override
    public void addPlayer(Instance channel, Instance body) {
        throw new UnsupportedOperationException("chat:addPlayer is the server's");
    }

    @Override
    public void removePlayer(Instance channel, Instance body) {
        throw new UnsupportedOperationException("chat:removePlayer is the server's");
    }

    @Override
    public void open(String prefill) {
        ChatInputBar bar = ChatLook.inputBar();
        if (bar != null && !bar.enabled) return;
        Minecraft.getInstance().setScreen(new ChatScreen(prefill, false));
    }

    @Override
    public void close() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ChatScreen) client.setScreen(null);
    }

    @Override
    public boolean isOpen() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    @Override
    public void clear() {
        clearLocal();
    }

    @Override
    public void setTarget(Instance channel) {
        if (channel != null && !(channel instanceof TextChannel)) throw new IllegalArgumentException("chat:setTarget wants a TextChannel");
        target = channel;
        if (channel != null) unread.remove(channel.id());
    }

    @Override
    public Instance target() {
        if (target != null) return target;
        ChatInputBar bar = ChatLook.inputBar();
        if (bar != null && bar.targetChannel instanceof TextChannel picked) return picked;
        List<TextChannel> mine = channels();
        return mine.isEmpty() ? null : mine.getFirst();
    }

    @Override
    public void bubble(Instance target, String text, Map<String, Object> look) {
        Bubbles.add(target, text, look);
    }

    @Override
    public List<Map<String, Object>> messages() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Iterator<Shown> it = lines.iterator(); it.hasNext(); ) {
            Shown shown = it.next();
            if (shown.removed < 0) out.add(shown.line.toMap(ClientScene.tree()));
        }
        return out;
    }
}
