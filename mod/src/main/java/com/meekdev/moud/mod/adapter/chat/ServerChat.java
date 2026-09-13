package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.ChatCommand;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.engine.ScriptEngine;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// what players type, while a place is running: the place formats it and it goes out as a system
// message, so the game's signed chat and its reporting never see a line the place rewrote
public final class ServerChat implements ChatRef {

    public static final ServerChat INSTANCE = new ServerChat();

    private ServerChat() {}

    @Override
    public void say(String markup, String player) {
        MinecraftServer server = ServerScene.server();
        if (server == null) return;
        if (player.isEmpty()) {
            server.getPlayerList().broadcastSystemMessage(ChatText.of(markup), false);
            return;
        }
        try {
            ServerPlayer to = server.getPlayerList().getPlayer(UUID.fromString(player));
            if (to != null) to.sendSystemMessage(ChatText.of(markup));
        } catch (IllegalArgumentException notAPlayer) {
            // a place's own character has a label for an owner and no chat to send to
        }
    }

    // on the server thread. false leaves the message to the game
    public static boolean message(ServerPlayer player, String text) {
        ScriptEngine vm = vm();
        if (vm == null) return false;
        InstanceTree tree = ServerScene.tree();
        Character body = Physics.bodies().of(player, tree);
        String line = vm.chatted(body, player.getGameProfile().name(), text);
        if (line != null) player.level().getServer().getPlayerList().broadcastSystemMessage(ChatText.of(line), false);
        return true;
    }

    // a slash command a ChatCommand in the tree answers. false leaves it to the game's own commands
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
}
