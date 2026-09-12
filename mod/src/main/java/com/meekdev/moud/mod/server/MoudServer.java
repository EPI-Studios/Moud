package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Humanoids;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.instance.Stages;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.client.Mirror;
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
        place = null;
        ServerScene.stop();
    }

    // the place ticks where its tree lives, which is the only thread allowed to write it, and
    // before the levels do
    //
    // 7.5 puts the stepped signal at 3 and the bkun step at 4, and the end of the server tick is
    // past both: bkun's sub level tick runs inside tickChildren, so on the end hook it copied the
    // body to the entity before the script had said where the part was this tick. what was drawn
    // came from the part and what was collided against came from the entity, one whole tick apart
    // -- which at seven metres and 2.6 rad/s is most of a block of the deck sitting inside you
    private static void tick(MinecraftServer server) {
        if (place == null) return;
        if (place.pollReload()) respawnAll(server);
        // taken once: the clock advances when it is read, so a second read is a second tick as
        // far as anything measuring seconds is concerned
        double dt = TICK.tick();
        ScriptEngine vm = place.vm();
        if (vm != null) vm.step(dt);
        // after the place has written, before the drain: a body reshaped this tick crosses with
        // the write that reshaped it rather than a tick behind it
        // life and the walking come before the rig, because a body that walked this tick is at
        // a different place and the rig has to settle it there
        Humanoids.follow(ServerScene.tree(), dt);
        Rig.follow(ServerScene.tree());
        Stages.run(ServerScene.tree(), Stage.COMPOSE, 0);
        Mirror.record(change -> Physics.apply(ServerScene.tree(), change, server));
        Physics.settle();
        // before anything else this tick: a place that hears a client and then steps is a place that
        // acts on this tick's input rather than on last tick's
        Post.drainToServer(ServerScene.tree());
        Physics.bodies().follow(server, ServerScene.tree(), Physics.shapes());
    }

    // every player gets a character, because a place that never mentions one still has to be
    // walkable. it lives in the tree like anything else, so a place tunes it by writing to it
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

    // a reload destroys everything under the world, characters included, and the fresh vm has
    // never seen anyone join. so everyone connected joins again: a new character, a new profile,
    // and the place's own joined handler deciding where they land
    private static void respawnAll(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Physics.bodies().release(player);
            spawn(player);
        }
    }

    // the character goes with the player, or a place that has been joined a hundred times holds
    // a hundred of them
    private static void leave(ServerPlayer player) {
        ScriptEngine vm = place == null ? null : place.vm();
        if (vm != null) vm.leaving(new JoinedPlayer(player));

        Character character = Physics.bodies().of(player, ServerScene.tree());
        Physics.bodies().release(player);
        if (character != null) Instances.destroy(character);
    }
}
