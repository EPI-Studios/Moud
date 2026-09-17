package com.meekdev.moud.mod.server.tool;

import com.meekdev.moud.core.character.Backpack;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Tool;
import com.meekdev.moud.core.character.Tools;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.payload.ToolPayload;
import com.meekdev.moud.script.api.ToolRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

public final class ServerTools implements ToolRef {

    private static final int SLOTS = 9;
    private static final double DROP_AHEAD = 2;

    private record Press(UUID player, boolean down) {}

    private static final Queue<Press> PRESSES = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Tool[]> SLOTTED = new HashMap<>();
    private static final Map<UUID, Integer> EMPTIED = new HashMap<>();
    private static final Map<Tool, Dropped> DROPPED = new WeakHashMap<>();
    private static final long PICKUP_DELAY_MILLIS = 1000;
    private static final long DROP_GRACE_MILLIS = 4000;

    private record Dropped(Character by, long at) {}

    public static final ServerTools INSTANCE = new ServerTools();

    private ServerTools() {}

    public static void listen() {
        ServerPlayNetworking.registerGlobalReceiver(ToolPayload.TYPE, (payload, context) ->
                PRESSES.add(new Press(context.player().getUUID(), payload.down())));
    }

    public static void tick(MinecraftServer server, @Nullable InstanceTree tree) {
        if (tree == null) return;
        List<Character> bodies = new ArrayList<>();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character body) bodies.add(body);
        }
        long now = System.currentTimeMillis();
        DROPPED.values().removeIf(drop -> now - drop.at() > DROP_GRACE_MILLIS);
        for (Tool tool : List.copyOf(tree.ofClass(Classes.TOOL))) {
            Dropped dropped = DROPPED.get(tool);
            List<Character> takers = dropped == null ? bodies : bodies.stream().filter(body -> body != dropped.by()).toList();
            if (dropped != null && now - dropped.at() < PICKUP_DELAY_MILLIS) continue;
            Character taker = Tools.pickedUpBy(tool, takers);
            if (taker != null) {
                DROPPED.remove(tool);
                Instances.reparent(tool, Tools.backpack(taker));
            }
        }
        for (Character body : bodies) Tools.hold(body, true);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Character body = Physics.bodies().of(player, tree);
            if (body == null) {
                clear(player);
                continue;
            }
            sync(player, body);
        }
        for (Press press; (press = PRESSES.poll()) != null; ) {
            ServerPlayer player = server.getPlayerList().getPlayer(press.player());
            Character body = player == null ? null : Physics.bodies().of(player, tree);
            Tool tool = body == null ? null : Tools.held(body);
            if (tool != null && !tool.manualActivationOnly) Tools.activate(tool, press.down());
        }
    }

    private static Tool[] assign(ServerPlayer player, Character body) {
        Tool[] slots = SLOTTED.computeIfAbsent(player.getUUID(), id -> new Tool[SLOTS]);
        Tool held = Tools.held(body);
        List<Tool> owned = new ArrayList<>();
        if (held != null) owned.add(held);
        owned.addAll(Tools.carried(body));
        for (int n = 0; n < SLOTS; n++) {
            if (slots[n] != null && !owned.contains(slots[n])) slots[n] = null;
        }
        for (Tool tool : owned) {
            if (indexOf(slots, tool) >= 0) continue;
            for (int n = 0; n < SLOTS; n++) {
                if (slots[n] == null) {
                    slots[n] = tool;
                    break;
                }
            }
        }
        return slots;
    }

    private static void sync(ServerPlayer player, Character body) {
        Tool[] slots = assign(player, body);
        Tool held = Tools.held(body);
        boolean managed = false;
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (int n = 0; n < SLOTS; n++) {
            if (slots[n] == null) {
                if (tagged(inventory.getItem(n))) {
                    inventory.setItem(n, ItemStack.EMPTY);
                    changed = true;
                }
                continue;
            }
            managed = true;
            ItemStack want = stack(slots[n]);
            if (!ItemStack.isSameItemSameComponents(want, inventory.getItem(n))) {
                inventory.setItem(n, want);
                changed = true;
            }
        }
        for (int n = SLOTS; n < inventory.getContainerSize(); n++) {
            if (tagged(inventory.getItem(n))) {
                inventory.setItem(n, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) player.inventoryMenu.broadcastChanges();
        if (!managed) return;
        int selectedSlot = inventory.getSelectedSlot();
        Integer emptied = EMPTIED.get(player.getUUID());
        if (emptied != null && emptied != selectedSlot) EMPTIED.remove(player.getUUID());
        if (EMPTIED.containsKey(player.getUUID())) return;
        Tool selected = slots[selectedSlot];
        if (selected != null && selected != held && selected.parent() != null) Tools.equip(body, selected);
        else if (selected == null && held != null) Tools.unequip(body);
    }

    @Override
    public void equip(Character body, Tool tool) {
        ServerPlayer player = playerOf(body);
        if (player == null) {
            Tools.equip(body, tool);
            return;
        }
        if (!Tools.canEquip(tool)) return;
        EMPTIED.remove(player.getUUID());
        if (tool.parent() != body && !(tool.parent() instanceof Backpack)) Instances.reparent(tool, Tools.backpack(body));
        Tools.equip(body, tool);
        Tool[] slots = assign(player, body);
        int slot = indexOf(slots, tool);
        if (slot >= 0 && slot != player.getInventory().getSelectedSlot()) {
            player.getInventory().setSelectedSlot(slot);
            player.connection.send(new ClientboundSetHeldSlotPacket(slot));
        }
    }

    @Override
    public void unequip(Character body) {
        Tools.unequip(body);
        ServerPlayer player = playerOf(body);
        if (player != null) EMPTIED.put(player.getUUID(), player.getInventory().getSelectedSlot());
    }

    public static boolean drop(ServerPlayer player) {
        InstanceTree tree = ServerScene.tree();
        Character body = tree == null ? null : Physics.bodies().of(player, tree);
        Tool held = body == null ? null : Tools.held(body);
        if (held == null) {
            if (!tagged(player.getInventory().getSelectedItem())) return false;
            player.inventoryMenu.sendAllDataToRemote();
            return true;
        }
        if (held.canBeDropped && held.child(Tools.HANDLE) instanceof Part handle && ServerScene.world() != null) {
            CFrame frame = Transforms.world(body);
            Vector3 ahead = frame.position().add(frame.lookVector().mul(DROP_AHEAD)).add(new Vector3(0, body.height * 0.5, 0));
            Instances.reparent(held, ServerScene.world());
            held.unequipped.fire(held);
            DROPPED.put(held, new Dropped(body, System.currentTimeMillis()));
            Instances.setObj(handle, Classes.SPATIAL.property("cframe"), Transforms.localFor(handle, CFrame.at(ahead)));
            sync(player, body);
        }
        player.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    public static boolean throwing(ServerPlayer player, ItemStack stack) {
        if (!tagged(stack)) return false;
        player.inventoryMenu.sendAllDataToRemote();
        return true;
    }

    public static void clear(ServerPlayer player) {
        EMPTIED.remove(player.getUUID());
        Tool[] slots = SLOTTED.remove(player.getUUID());
        if (slots == null) return;
        Inventory inventory = player.getInventory();
        for (int n = 0; n < SLOTS; n++) {
            if (tagged(inventory.getItem(n))) inventory.setItem(n, ItemStack.EMPTY);
        }
    }

    private static @Nullable ServerPlayer playerOf(Character body) {
        MinecraftServer server = ServerScene.server();
        if (server == null || !body.hasPlayer()) return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(body.owner));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int indexOf(Tool[] slots, Tool tool) {
        for (int n = 0; n < slots.length; n++) {
            if (slots[n] == tool) return n;
        }
        return -1;
    }

    private static ItemStack stack(Tool tool) {
        Item item = Items.STICK;
        Identifier id = tool.item.isEmpty() ? null : Identifier.tryParse(tool.item);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(tool.toolTip.isEmpty() ? tool.name() : tool.toolTip));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static final String TAG = "moudTool";

    private static boolean tagged(ItemStack stack) {
        CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(TAG, false);
    }
}
