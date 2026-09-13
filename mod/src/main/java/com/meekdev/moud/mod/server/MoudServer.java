package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.transport.Broadcast;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.instance.Stages;
import com.meekdev.moud.core.query.Touches;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.script.engine.ScriptEngine;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.jspecify.annotations.Nullable;

public final class MoudServer {

    private static @Nullable Place place;
    private static final Clock TICK = new Clock();

    private MoudServer() {}

    public static @Nullable Place place() {
        return place;
    }

    public static void install() {
        Walkers.pilot(ServerPilot.INSTANCE);
        ServerLifecycleEvents.SERVER_STARTED.register(MoudServer::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped());
        ServerTickEvents.START_SERVER_TICK.register(MoudServer::tick);
        ServerPlayerEvents.JOIN.register(MoudServer::spawn);
        ServerPlayerEvents.LEAVE.register(MoudServer::leave);
    }

    private static void started(MinecraftServer server) {
        ServerScene.start(server);
        place = Place.server(ServerScene.world(), Addons.classes());
        place.start();
        MoudMod.LOG.info("place is running on the server");
    }

    private static void stopped() {
        ServerChat.INSTANCE.stop();
        ServerHistory.INSTANCE.clear();
        Broadcast.stop();
        place = null;
        ServerScene.stop();
    }

    private static void tick(MinecraftServer server) {
        if (place == null) return;
        if (place.pollReload()) respawnAll(server);
        double dt = TICK.tick();
        ScriptEngine vm = place.vm();
        if (vm != null) vm.step(dt);
        Humanoids.follow(ServerScene.tree(), dt);
        Rig.follow(ServerScene.tree());
        Stages.run(ServerScene.tree(), Stage.COMPOSE, 0);
        Touches.step(ServerScene.tree());
        Zones.step(ServerScene.tree(), Addons.classes(), System.nanoTime() / 1e9);
        ServerChat.INSTANCE.zones(ServerScene.tree());
        Broadcast.tick(server, change -> {
            Physics.apply(ServerScene.tree(), change, server);
            ServerHistory.INSTANCE.note(ServerScene.tree(), change);
        });
        Physics.settle();
        Post.drainToServer(ServerScene.tree());
        ServerChat.INSTANCE.tick(server);
        ServerPrompts.tick(server);
        ServerPilot.tick(server);
        Physics.bodies().follow(server, ServerScene.tree(), Physics.shapes());
        ServerHistory.INSTANCE.record(ServerScene.tree());
    }

    private static void spawn(ServerPlayer player) {
        Instance world = ServerScene.world();
        if (place == null || world == null) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();

        Character character = Instances.create(Classes.CHARACTER, world,
                player.getGameProfile().name());
        Physics.bodies().bind(player, character);
        player.teleportTo(0.5, 70.0, 0.5);

        ScriptEngine vm = place.vm();
        if (vm != null) vm.joined(new JoinedPlayer(player));
    }

    private static void respawnAll(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Physics.bodies().release(player);
            spawn(player);
        }
    }

    private static void leave(ServerPlayer player) {
        ScriptEngine vm = place == null ? null : place.vm();
        if (vm != null) vm.leaving(new JoinedPlayer(player));

        Character character = Physics.bodies().of(player, ServerScene.tree());
        Physics.bodies().release(player);
        if (character != null) Instances.destroy(character);
        Broadcast.forget(player.getUUID());
    }
}
