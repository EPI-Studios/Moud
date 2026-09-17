package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.instance.Stages;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.query.Touches;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.adapter.player.EditorBody;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.input.ServerClicks;
import com.meekdev.moud.mod.server.pilot.ServerPilot;
import com.meekdev.moud.mod.server.tool.ServerTools;
import com.meekdev.moud.mod.server.zone.ServerPrompts;
import com.meekdev.moud.mod.transport.Broadcast;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.script.host.Host;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        Editing.install();
        ServerLifecycleEvents.SERVER_STARTED.register(MoudServer::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped());
        ServerTickEvents.START_SERVER_TICK.register(MoudServer::tick);
        ServerPlayerEvents.JOIN.register(MoudServer::spawn);
        ServerPlayerEvents.LEAVE.register(MoudServer::leave);
        ServerPlayerEvents.AFTER_RESPAWN.register(MoudServer::respawned);
    }

    private static void respawned(ServerPlayer before, ServerPlayer after, boolean alive) {
        if (place == null || place.editing()) return;
        Character character = Physics.bodies().of(after, ServerScene.tree());
        if (character != null) Physics.bodies().bind(after, character);
        if (!alive) Spawning.respawned(after);
    }

    private static void started(MinecraftServer server) {
        ServerScene.start(server);
        place = Place.server(ServerScene.world(), Addons.classes());
        place.start();
        MoudMod.LOG.info("place is running on the server");
    }

    private static void stopped() {
        if (place != null) place.close();
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
        Host host = place.host();
        if (host != null) host.step(dt);
        Humanoids.follow(ServerScene.tree(), dt);
        if (!place.editing()) Animators.step(ServerScene.tree(), dt, true);
        Rig.follow(ServerScene.tree());
        Stages.run(ServerScene.tree(), Stage.COMPOSE, 0);
        Touches.step(ServerScene.tree());
        Zones.step(ServerScene.tree(), Addons.classes(), System.nanoTime() / 1e9);
        ServerChat.INSTANCE.zones(ServerScene.tree());
        Broadcast.tick(server, change -> {
            Physics.apply(ServerScene.tree(), change, server);
            ServerHistory.INSTANCE.note(ServerScene.tree(), change);
        });
        if (place.reloadedFully()) Post.wired().sendReloaded(server);
        Physics.settle(ServerScene.tree(), !place.editing());
        Post.drainToServer(ServerScene.tree());
        ServerChat.INSTANCE.tick(server);
        ServerPrompts.tick(server);
        ServerClicks.tick(server);
        ServerPilot.tick(server);
        Physics.bodies().follow(server, ServerScene.tree(), Physics.shapes());
        if (!place.editing()) HumanoidStates.tick(server, ServerScene.tree());
        if (!place.editing()) ServerTools.tick(server, ServerScene.tree());
        ServerHistory.INSTANCE.record(ServerScene.tree());
        Editing.tick(server);
        if (!place.editing()) Spawning.tick(server, dt);
    }

    private static void spawn(ServerPlayer player) {
        Instance world = ServerScene.world();
        if (place == null || world == null) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
        EditorBody.set(player, place.editing());
        if (place.editing()) {
            player.teleportTo(Spawning.FALLBACK.x(), Spawning.FALLBACK.y(), Spawning.FALLBACK.z());
            return;
        }

        boolean fresh = Spawning.autoSpawn() && Spawning.place(player, null);
        if (!Spawning.autoSpawn()) Spawning.hold(player);

        Host host = place.host();
        if (host != null) host.joined(new JoinedPlayer(player));
        if (fresh) Spawning.announce(player);
    }

    static void respawnAll(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Physics.bodies().release(player);
            spawn(player);
        }
    }

    private static void leave(ServerPlayer player) {
        Host host = place == null ? null : place.host();
        if (host != null) host.leaving(new JoinedPlayer(player));

        Character character = Physics.bodies().of(player, ServerScene.tree());
        Physics.bodies().release(player);
        if (character != null) Instances.destroy(character);
        Spawning.left(player);
        HumanoidStates.left(player);
        ServerTools.clear(player);
        Broadcast.forget(player.getUUID());
    }
}
