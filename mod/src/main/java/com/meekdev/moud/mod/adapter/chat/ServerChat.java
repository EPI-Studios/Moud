package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.chat.ChatCommand;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.chat.TextChannel;
import com.meekdev.moud.core.chat.TextSource;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.engine.ScriptEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.meekdev.moud.mod.transport.payload.ChatDownPayload;
import com.meekdev.moud.mod.transport.payload.ChatUpPayload;

public final class ServerChat implements ChatRef {

    public static final ServerChat INSTANCE = new ServerChat();

    private static final int KEPT = 2000;

    private static final PropertyDef BODY = Classes.TEXT_SOURCE.property("body");

    private static final int BURST = 6;
    private static final double REFILL_PER_SECOND = 1.5;

    private record Typed(UUID player, int channel, String text) {}

    private static final class Kept {
        final ChatLine line;
        final Set<UUID> reached;

        Kept(ChatLine line, Set<UUID> reached) {
            this.line = line;
            this.reached = reached;
        }
    }

    private final Queue<Typed> typed = new ConcurrentLinkedQueue<>();
    private final LinkedHashMap<Long, Kept> kept = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Kept> eldest) {
            return size() > KEPT;
        }
    };
    private final Map<UUID, double[]> buckets = new HashMap<>();
    private final Map<String, Long> lastSent = new HashMap<>();
    private final Set<String> leftOut = new HashSet<>();
    private long nextId = 1;

    private final Map<Zone, Set<String>> zoned = new HashMap<>();

    private ServerChat() {}

    public void zones(InstanceTree tree) {
        MinecraftServer server = ServerScene.server();
        if (tree == null || server == null) return;
        zoned.keySet().removeIf(zone -> !zone.isAlive());
        for (Zone zone : tree.ofClass(Classes.ZONE)) {
            Set<String> added = zoned.computeIfAbsent(zone, key -> new HashSet<>());
            Set<String> inside = new HashSet<>();
            if (zone.textChannel instanceof TextChannel channel) {
                for (Instance occupant : zone.occupants()) {
                    if (!(occupant instanceof Character body) || !body.hasPlayer()) continue;
                    inside.add(body.owner);
                    ServerPlayer player = playerOf(server, body.owner);
                    if (player != null && sourceOf(channel, body.owner) == null) {
                        join(channel, player);
                        added.add(body.owner);
                    }
                }
                for (String player : new ArrayList<>(added)) {
                    if (inside.contains(player)) continue;
                    TextSource source = sourceOf(channel, player);
                    if (source != null) Instances.destroy(source);
                    added.remove(player);
                }
            }
        }
    }

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(ChatUpPayload.TYPE, (payload, context) ->
                INSTANCE.typed.add(new Typed(context.player().getUUID(), payload.channel(), payload.text())));
    }

    public void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null || vm() == null) {
            typed.clear();
            return;
        }
        members(server, tree);
        for (Typed one; (one = typed.poll()) != null; ) {
            ServerPlayer player = server.getPlayerList().getPlayer(one.player());
            if (player != null) fromPlayer(player, tree, one.channel(), one.text());
        }
    }

    public void stop() {
        typed.clear();
        kept.clear();
        buckets.clear();
        lastSent.clear();
        leftOut.clear();
    }

    private void members(MinecraftServer server, InstanceTree tree) {
        Set<String> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID().toString());
        for (TextChannel channel : tree.ofClass(Classes.TEXT_CHANNEL)) {
            Set<String> present = new HashSet<>();
            for (Instance child : new ArrayList<>(channel.children())) {
                if (!(child instanceof TextSource source)) continue;
                if (!online.contains(source.player)) {
                    Instances.destroy(source);
                    continue;
                }
                present.add(source.player);
                ServerPlayer wearer = playerOf(server, source.player);
                Character body = wearer == null ? null : Physics.bodies().of(wearer, tree);
                if (source.body != body) Instances.setObj(source, BODY, body);
            }
            if (!channel.autoJoin) continue;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String id = player.getUUID().toString();
                if (present.contains(id) || leftOut.contains(channel.id() + "|" + id)) continue;
                join(channel, player);
            }
        }
    }

    private static TextSource join(TextChannel channel, ServerPlayer player) {
        String id = player.getUUID().toString();
        Character body = Physics.bodies().of(player, channel.tree());
        return Instances.create(Classes.TEXT_SOURCE, channel, player.getGameProfile().name(), source -> {
            source.player = id;
            source.body = body;
        });
    }

    private static Set<String> tagsOf(TextChannel channel, TextSource source) {
        String list = source != null && !source.richText.isBlank() ? source.richText
                : channel != null ? channel.richText : "";
        if (list.isBlank()) return Set.of();
        Set<String> tags = new HashSet<>();
        for (String name : list.split(",")) {
            if (!name.isBlank()) tags.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    private static TextSource sourceOf(TextChannel channel, String player) {
        for (Instance child : channel.children()) {
            if (child instanceof TextSource source && source.player.equals(player)) return source;
        }
        return null;
    }

    public static boolean vanilla(ServerPlayer player, String text) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null || vm() == null) return false;
        INSTANCE.fromPlayer(player, tree, -1, text);
        return true;
    }

    private void fromPlayer(ServerPlayer player, InstanceTree tree, int channelId, String text) {
        String id = player.getUUID().toString();
        TextChannel channel = tree.byId(channelId) instanceof TextChannel picked ? picked : first(tree, id);
        TextSource source = channel == null ? null : sourceOf(channel, id);
        Character body = Physics.bodies().of(player, tree);

        ChatLine line = new ChatLine(nextId++);
        line.channel = channel == null ? -1 : channel.id();
        line.source = source == null ? -1 : source.id();
        line.body = body == null ? -1 : body.id();
        line.text = RichText.allow(text, tagsOf(channel, source));
        line.prefix = RichText.escape(player.getGameProfile().name());
        line.timestamp = System.currentTimeMillis();

        String status = refuse(player, channel, source, text);
        ScriptEngine vm = vm();
        if (status == null && channel != null && Boolean.FALSE.equals(channel.shouldSend.first(true, line.toMap(tree)))) {
            status = "Blocked";
        }
        if (status == null && vm != null) {
            Object[] allowed = vm.chatHook("shouldSend", line.toMap(tree));
            if (allowed != null && allowed.length > 0 && Boolean.FALSE.equals(allowed[0])) status = "Blocked";
        }
        if (status != null) {
            line.status = status;
            send(player, line.packet(ChatDownPayload.STATUS));
            return;
        }
        lastSent.put((channel == null ? -1 : channel.id()) + "|" + id, System.currentTimeMillis());
        deliver(tree, channel, line, null);
    }

    private String refuse(ServerPlayer player, TextChannel channel, TextSource source, String text) {
        if (channel != null && source == null) return "NotInChannel";
        if (source != null && !source.canSend) return "Muted";
        int max = channel == null ? 256 : channel.maxLength;
        if (text.isBlank()) return "Empty";
        if (text.length() > max) return "TooLong";
        long now = System.currentTimeMillis();
        if (channel != null && channel.slowMode > 0) {
            Long last = lastSent.get(channel.id() + "|" + player.getUUID());
            if (last != null && now - last < channel.slowMode * 1000) return "SlowMode";
        }
        double[] bucket = buckets.computeIfAbsent(player.getUUID(), key -> new double[] {BURST, now});
        bucket[0] = Math.min(BURST, bucket[0] + (now - bucket[1]) / 1000.0 * REFILL_PER_SECOND);
        bucket[1] = now;
        if (bucket[0] < 1) return "Floodchecked";
        bucket[0] -= 1;
        return null;
    }

    private static TextChannel first(InstanceTree tree, String player) {
        for (TextChannel channel : tree.ofClass(Classes.TEXT_CHANNEL)) {
            if (sourceOf(channel, player) != null) return channel;
        }
        return null;
    }

    private long deliver(InstanceTree tree, TextChannel channel, ChatLine line, UUID only) {
        ScriptEngine vm = vm();
        if (channel != null) line.apply(channel.onIncoming.first(null, line.toMap(tree)));
        if (vm != null) {
            Object[] changed = vm.chatHook("onIncoming", line.toMap(tree));
            if (changed != null && changed.length > 0) line.apply(changed[0]);
        }
        MinecraftServer server = ServerScene.server();
        if (server == null) return line.id;
        Set<UUID> reached = new HashSet<>();
        Map<String, Object> asMap = line.toMap(tree);
        if (only != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(only);
            if (player != null && shouldDeliver(tree, channel, asMap, player)) {
                send(player, line.packet(ChatDownPayload.LINE));
                reached.add(only);
            }
        } else if (channel != null) {
            for (Instance child : channel.children()) {
                if (!(child instanceof TextSource source)) continue;
                ServerPlayer player = playerOf(server, source.player);
                if (player == null) continue;
                if (Boolean.FALSE.equals(channel.shouldDeliver.first(true, asMap, source))) continue;
                send(player, line.packet(ChatDownPayload.LINE));
                reached.add(player.getUUID());
            }
        } else {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player, line.packet(ChatDownPayload.LINE));
                reached.add(player.getUUID());
            }
        }
        kept.put(line.id, new Kept(line, reached));
        if (channel != null) channel.messageReceived.fire(asMap);
        if (vm != null) vm.chatEvent("messageReceived", asMap);
        return line.id;
    }

    private static boolean shouldDeliver(InstanceTree tree, TextChannel channel, Map<String, Object> message, ServerPlayer player) {
        if (channel == null || !channel.shouldDeliver.isSet()) return true;
        TextSource source = sourceOf(channel, player.getUUID().toString());
        return !Boolean.FALSE.equals(channel.shouldDeliver.first(true, message, source));
    }

    private static void send(ServerPlayer player, ChatDownPayload packet) {
        if (ServerPlayNetworking.canSend(player, ChatDownPayload.TYPE)) {
            ServerPlayNetworking.send(player, packet);
        } else if (packet.kind() == ChatDownPayload.LINE) {
            String prefix = packet.prefix().isEmpty() ? "" : packet.prefix() + ": ";
            player.sendSystemMessage(ChatText.of(prefix + packet.text()));
        }
    }

    private static ServerPlayer playerOf(MinecraftServer server, String id) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean command(ServerPlayer player, String line) {
        if (vm() == null) return false;
        InstanceTree tree = ServerScene.tree();
        boolean ran = false;
        for (ChatCommand command : tree.ofClass(Classes.CHAT_COMMAND)) {
            List<String> args = command.match(line);
            if (args == null) continue;
            command.invoked.fire(new ChatCommand.Invoked(Physics.bodies().of(player, tree), "/" + line, args));
            ran = true;
        }
        return ran;
    }

    private static ScriptEngine vm() {
        Place place = MoudServer.place();
        return place == null || !ServerScene.running() ? null : place.vm();
    }

    @Override
    public long send(Map<String, Object> message) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return -1;
        TextChannel channel = message.get("channel") instanceof TextChannel picked ? picked : null;
        ChatLine line = new ChatLine(nextId++);
        line.channel = channel == null ? -1 : channel.id();
        line.text = String.valueOf(message.getOrDefault("text", ""));
        line.prefix = message.get("prefix") instanceof String prefix ? prefix : "";
        line.metadata = message.get("metadata") instanceof String metadata ? metadata : "";
        line.timestamp = System.currentTimeMillis();
        if (message.get("from") instanceof Character body) {
            line.body = body.id();
            if (channel != null) {
                TextSource source = sourceOf(channel, body.owner);
                line.source = source == null ? -1 : source.id();
            }
            if (line.prefix.isEmpty()) line.prefix = RichText.escape(body.name());
        }
        UUID only = null;
        if (message.get("to") instanceof Character to) {
            try {
                only = UUID.fromString(to.owner);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("chat:send to expects a player body");
            }
        }
        return deliver(tree, channel, line, only);
    }

    @Override
    public void edit(long id, Map<String, Object> changes) {
        Kept one = kept.get(id);
        if (one == null) throw new IllegalArgumentException("there is no message " + id + " to edit, or it is too old");
        one.line.apply(changes);
        push(one, ChatDownPayload.EDIT);
        ScriptEngine vm = vm();
        InstanceTree tree = ServerScene.tree();
        if (vm != null && tree != null) vm.chatEvent("edited", one.line.toMap(tree));
    }

    @Override
    public void delete(long id) {
        Kept one = kept.remove(id);
        if (one == null) return;
        push(one, ChatDownPayload.DELETE);
        ScriptEngine vm = vm();
        if (vm != null) vm.chatEvent("deleted", (double) id);
    }

    private void push(Kept one, int kind) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        for (UUID id : one.reached) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && ServerPlayNetworking.canSend(player, ChatDownPayload.TYPE)) {
                ServerPlayNetworking.send(player, one.line.packet(kind));
            }
        }
    }

    @Override
    public void addPlayer(Instance channel, Instance body) {
        if (!(channel instanceof TextChannel text)) throw new IllegalArgumentException("chat:addPlayer wants a TextChannel");
        ServerPlayer player = playerOf(body);
        leftOut.remove(text.id() + "|" + player.getUUID());
        if (sourceOf(text, player.getUUID().toString()) == null) join(text, player);
    }

    @Override
    public void removePlayer(Instance channel, Instance body) {
        if (!(channel instanceof TextChannel text)) throw new IllegalArgumentException("chat:removePlayer expects a TextChannel");
        ServerPlayer player = playerOf(body);
        leftOut.add(text.id() + "|" + player.getUUID());
        TextSource source = sourceOf(text, player.getUUID().toString());
        if (source != null) Instances.destroy(source);
    }

    private static ServerPlayer playerOf(Instance body) {
        MinecraftServer server = ServerScene.server();
        ServerPlayer player = !(body instanceof Character character) || server == null ? null : playerOf(server, character.owner);
        if (player == null) throw new IllegalArgumentException("wants a body a player is wearing");
        return player;
    }

    private static UnsupportedOperationException clientOnly(String what) {
        return new UnsupportedOperationException("chat:" + what + " is a client's, the server has no chat window");
    }

    @Override
    public void open(String prefill) {
        throw clientOnly("open");
    }

    @Override
    public void close() {
        throw clientOnly("close");
    }

    @Override
    public boolean isOpen() {
        throw clientOnly("isOpen");
    }

    @Override
    public void clear() {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, ChatDownPayload.TYPE)) {
                ServerPlayNetworking.send(player, new ChatLine(0).packet(ChatDownPayload.CLEAR));
            }
        }
    }

    @Override
    public void setTarget(Instance channel) {
        throw clientOnly("setTarget");
    }

    @Override
    public Instance target() {
        throw clientOnly("getTarget");
    }

    @Override
    public void bubble(Instance target, String text, Map<String, Object> look) {
        throw clientOnly("bubble");
    }

    @Override
    public List<Map<String, Object>> messages() {
        InstanceTree tree = ServerScene.tree();
        List<Map<String, Object>> out = new ArrayList<>();
        if (tree == null) return out;
        for (Kept one : kept.values()) out.add(one.line.toMap(tree));
        return out;
    }
}
