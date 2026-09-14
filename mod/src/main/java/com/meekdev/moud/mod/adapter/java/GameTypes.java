package com.meekdev.moud.mod.adapter.java;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.chat.ChatText;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.script.host.java.Conversions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class GameTypes {

    private GameTypes() {}

    public static void install(Supplier<@Nullable InstanceTree> clientTree, Function<UUID, @Nullable Player> clientPlayers) {
        Conversions.register(BlockPos.class, pos -> new Vector3(pos.getX(), pos.getY(), pos.getZ()),
                (value, want) -> value instanceof Vector3 v ? BlockPos.containing(v.x(), v.y(), v.z()) : null);
        Conversions.register(Vec3.class, vec -> new Vector3(vec.x, vec.y, vec.z),
                (value, want) -> value instanceof Vector3 v ? new Vec3(v.x(), v.y(), v.z()) : null);
        Conversions.register(Identifier.class, Identifier::toString,
                (value, want) -> value instanceof String text ? Identifier.tryParse(text) : null);
        Conversions.register(Component.class, ChatText::markup,
                (value, want) -> value instanceof String text ? ChatText.of(text) : null);
        Conversions.register(ItemStack.class, GameTypes::item, (value, want) -> value instanceof Map<?, ?> map ? stack(map) : null);
        Conversions.register(Player.class, player -> body(player, clientTree),
                (value, want) -> value instanceof Character body ? player(body, clientPlayers) : null);
    }

    private static boolean onServer() {
        MinecraftServer server = ServerScene.server();
        return server != null && server.isSameThread();
    }

    private static @Nullable Character body(Player player, Supplier<@Nullable InstanceTree> clientTree) {
        return Bodies.of(onServer() ? ServerScene.tree() : clientTree.get(), player.getUUID().toString());
    }

    private static @Nullable Player player(Character body, Function<UUID, @Nullable Player> clientPlayers) {
        if (!body.hasPlayer()) return null;
        UUID id = UUID.fromString(body.owner);
        if (onServer()) return ServerScene.server().getPlayerList().getPlayer(id);
        return clientPlayers.apply(id);
    }

    private static @Nullable Object item(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("count", (double) stack.getCount());
        return out;
    }

    private static @Nullable ItemStack stack(Map<?, ?> map) {
        if (!(map.get("id") instanceof String id)) return null;
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return null;
        int count = map.get("count") instanceof Number n ? n.intValue() : 1;
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key), count);
    }
}
