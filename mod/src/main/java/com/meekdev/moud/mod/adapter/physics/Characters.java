package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.physics.Physics;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

// the character instance drives the player's movement profile
//
// a place states metres and seconds and bkun states the same numbers per tick, so every field
// crossing here is divided by the tick rate. getting that wrong is a character that walks at
// twenty times the speed it asked for, which reads as a physics bug rather than a units one
public final class Characters {

    private static final double TICKS = 20.0;

    private static final PropertyDef CFRAME = Classes.CHARACTER.property("cframe");

    private final Map<UUID, Integer> bound = new HashMap<>();

    public static MovementProfile profileOf(Character character) {
        return MovementProfile.builder()
                .gravityScale(character.gravityScale)
                .maxGroundSpeed(character.walkSpeed / TICKS)
                .sprintMultiplier(character.sprintMultiplier)
                .sneakMultiplier(character.sneakMultiplier)
                .maxAirSpeed(character.airSpeed / TICKS)
                .groundAcceleration(character.groundAcceleration / (TICKS * TICKS))
                .groundDeceleration(character.groundDeceleration / (TICKS * TICKS))
                .airAcceleration(character.airAcceleration / (TICKS * TICKS))
                .slideAcceleration(character.slideAcceleration / (TICKS * TICKS))
                .jumpPower(character.jumpPower / TICKS)
                // a drag is what a second leaves you with, and it compounds every tick
                .airDrag(Math.pow(character.airDrag, 1.0 / TICKS))
                .fallDrag(Math.pow(character.fallDrag, 1.0 / TICKS))
                .stepHeight(character.stepHeight)
                .slideThresholdDegrees(character.slopeLimit)
                .coyoteTicks((int) Math.round(character.coyoteTime * TICKS))
                .jumpBufferTicks((int) Math.round(character.jumpBuffer * TICKS))
                .followSlopes(character.followSlopes)
                .moverShape(character.radius, character.height)
                .build();
    }

    // where the tree says a character is. the entity is authority, this is the view of it a
    // place reads, written once a tick from the same place everything else is
    public static void place(Character character, Vec3 position) {
        Instances.setObj(character, CFRAME, CFrame.at(position));
    }

    public void follow(MinecraftServer server, @Nullable InstanceTree tree) {
        if (tree == null) return;
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(tree.byId(entry.getValue()) instanceof Character character)) {
                continue;
            }
            place(character, new Vec3(player.getX(), player.getY(), player.getZ()));
        }
    }

    public void bind(ServerPlayer player, Character character) {
        bound.put(player.getUUID(), character.id());
        Physics.setProfile(player, profileOf(character));
    }

    public void release(ServerPlayer player) {
        if (bound.remove(player.getUUID()) != null) Physics.clearProfile(player);
    }

    public @Nullable Character of(ServerPlayer player, @Nullable InstanceTree tree) {
        Integer id = bound.get(player.getUUID());
        if (id == null || tree == null) return null;
        return tree.byId(id) instanceof Character character ? character : null;
    }

    // a place that writes walkSpeed in stepped expects to walk faster on the next tick, so the
    // profile is pushed again from the same change stream everything else follows
    //
    // the player is looked up rather than held: a respawn hands out a new ServerPlayer and a
    // stored one goes on taking writes nobody can see
    public void apply(InstanceTree source, Change change, MinecraftServer server) {
        if (!(change instanceof Change.Wrote wrote)) return;
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            if (entry.getValue() != wrote.id()) continue;
            Instance instance = source.byId(wrote.id());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (instance instanceof Character character && player != null) {
                Physics.setProfile(player, profileOf(character));
            }
        }
    }
}
