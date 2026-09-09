package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.client.Mirror;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.script.vm.Vm;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
    }

    private static void started(net.minecraft.server.MinecraftServer server) {
        ServerScene.start(server);
        place = new Place(ServerScene.world(), Classes.registry());
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
    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (place == null) return;
        place.pollReload();
        Vm vm = place.vm();
        if (vm != null) vm.step(TICK.tick());
        Mirror.record(change -> Physics.apply(ServerScene.tree(), change));
        Physics.settle();
    }

    // scaffolding until the character lands: nothing holds a player up in a void level
    private static void spawn(ServerPlayer player) {
        player.teleportTo(0.5, 70.0, 0.5);
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
    }
}
