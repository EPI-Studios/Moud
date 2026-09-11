package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.physics.Physics;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
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
    private static final PropertyDef OWNER = Classes.CHARACTER.property("owner");
    private static final PropertyDef LOOK_PITCH = Classes.CHARACTER.property("lookPitch");
    private static final PropertyDef LOOK_YAW = Classes.CHARACTER.property("lookYaw");
    private static final PropertyDef MOVE_DISTANCE = Classes.CHARACTER.property("moveDistance");
    private static final PropertyDef MOVE_SPEED = Classes.CHARACTER.property("moveSpeed");
    private static final PropertyDef CROUCHING = Classes.CHARACTER.property("crouching");

    // the properties the profile is built from, which is every one the class adds to a spatial.
    // a pose write is not one of them, and follow makes one of those every tick: pushing the
    // profile for it syncs the whole thing to the client twenty times a second
    private static final boolean[] PROFILE = profileProperties();

    private static boolean[] profileProperties() {
        PropertyDef[] all = Classes.CHARACTER.properties();
        boolean[] mine = new boolean[all.length];
        for (PropertyDef property : all) {
            mine[property.index()] = Classes.SPATIAL.property(property.name()) == null;
        }
        return mine;
    }

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
    public static void place(Character character, Vec3 position, double yawDegrees) {
        // the body faces where the player does. minecraft's yaw is the opposite way round from a
        // right handed turn and its zero looks down +z, which is half a turn from our forward
        double yaw = Math.PI - Math.toRadians(yawDegrees);
        Instances.setObj(character, CFRAME, new CFrame(position, Quat.euler(0, yaw, 0)));
    }

    public void follow(MinecraftServer server, @Nullable InstanceTree tree) {
        if (tree == null) return;
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(tree.byId(entry.getValue()) instanceof Character character)) {
                continue;
            }
            place(character, new Vec3(player.getX(), player.getY(), player.getZ()),
                    player.getYRot());
            animation(character, player);
        }
    }

    // the four numbers a body is animated from, rather than the six transforms they produce.
    // the client evaluates the same pose from the same state, which is what keeps a limb off the
    // wire twenty times a second and what lets the server rewind one for a hit test
    private static void animation(Character character, ServerPlayer player) {
        // the head turns against the body, and the body already faces where the player does
        double relative = Math.toRadians(player.getYHeadRot() - player.getYRot());
        Instances.setNum(character, LOOK_PITCH, Math.toRadians(player.getXRot()));
        Instances.setNum(character, LOOK_YAW, relative);
        Instances.setNum(character, MOVE_DISTANCE, player.walkAnimation.position());
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, player.walkAnimation.speed()));
        Instances.setBool(character, CROUCHING, player.isCrouching());
    }

    public void bind(ServerPlayer player, Character character) {
        bound.put(player.getUUID(), character.id());
        // the client finds whose skin this body wears by asking the level for this player
        Instances.setObj(character, OWNER, player.getUUID().toString());
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
        if (wrote.property() < 0 || wrote.property() >= PROFILE.length
                || !PROFILE[wrote.property()]) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            if (entry.getValue() != wrote.id()) continue;
            Instance instance = source.byId(wrote.id());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (instance instanceof Character character && player != null) {
                Physics.setProfile(player, profileOf(character));
                // radius, height, display and scale all shape the body, and the same write that
                // changed the profile is the one that has to rebuild it
                Rig.apply(character);
            }
        }
    }
}
