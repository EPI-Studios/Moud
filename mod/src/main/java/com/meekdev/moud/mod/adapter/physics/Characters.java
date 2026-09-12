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
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
    private static final PropertyDef ATTACK_TIME = Classes.CHARACTER.property("attackTime");
    private static final PropertyDef ATTACK_LEFT = Classes.CHARACTER.property("attackLeft");
    private static final PropertyDef SWIM_AMOUNT = Classes.CHARACTER.property("swimAmount");
    private static final PropertyDef RIDING = Classes.CHARACTER.property("riding");
    private static final PropertyDef FLYING = Classes.CHARACTER.property("flying");
    private static final PropertyDef IN_WATER = Classes.CHARACTER.property("inWater");
    private static final PropertyDef FLYING_TIME = Classes.CHARACTER.property("flyingTime");
    private static final PropertyDef FLYING_YAW = Classes.CHARACTER.property("flyingYaw");
    private static final PropertyDef DEATH_TIME = Classes.CHARACTER.property("deathTime");
    private static final PropertyDef SLEEPING = Classes.CHARACTER.property("sleeping");
    private static final PropertyDef BED_YAW = Classes.CHARACTER.property("bedYaw");
    private static final PropertyDef CRAWLING = Classes.CHARACTER.property("crawling");
    private static final PropertyDef SPINNING = Classes.CHARACTER.property("spinning");
    private static final PropertyDef FROZEN = Classes.CHARACTER.property("frozen");
    private static final PropertyDef HURT = Classes.CHARACTER.property("hurt");

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
            drive(character, player);
        }
    }

    // where a body is and how it is standing, from the player it belongs to. the client drives
    // your own through here too, off its own player: the state that went to the server and came
    // back is several ticks old, and a body that walks after you do is not the same body
    public static void drive(Character character, Player player) {
        // the body faces where the body faces, which is not where the player is looking. yRot is
        // the aim; yBodyRot lags it and only gets dragged round once the head has turned far
        // enough or the player walks. driving the body from the aim instead snapped it to the
        // mouse and left the head with nothing to turn against
        //
        // asleep it faces neither: the model throws the body's yaw away and lies it along the
        // bed, so the bed's heading goes on the frame in place of it rather than on top of it
        double heading = player.isSleeping() ? 180.0f - bedAngle(player) : player.yBodyRot;
        place(character, new Vec3(player.getX(), player.getY(), player.getZ()), heading);
        animation(character, player);
    }

    // the four the model states, by name rather than by ordinal
    private static float bedAngle(Player player) {
        Direction bed = player.getBedOrientation();
        if (bed == null) return 180.0f - player.yBodyRot;
        return switch (bed) {
            case SOUTH -> 90.0f;
            case WEST -> 0.0f;
            case NORTH -> 270.0f;
            case EAST -> 180.0f;
            default -> 0.0f;
        };
    }

    // the four numbers a body is animated from, rather than the six transforms they produce.
    // the client evaluates the same pose from the same state, which is what keeps a limb off the
    // wire twenty times a second and what lets the server rewind one for a hit test
    private static void animation(Character character, Player player) {
        // the head turns against the body, and the game has already clamped how far it may
        double relative = Math.toRadians(Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot));
        Instances.setNum(character, LOOK_PITCH, Math.toRadians(player.getXRot()));
        Instances.setNum(character, LOOK_YAW, relative);
        Instances.setNum(character, MOVE_DISTANCE, player.walkAnimation.position());
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, player.walkAnimation.speed()));
        Instances.setBool(character, CROUCHING, player.isCrouching());

        // the swing runs backward in the entity: attackAnim counts down from one as the arm
        // returns, and the model reads it as how far through the swing the arm is
        Instances.setNum(character, ATTACK_TIME, player.getAttackAnim(1.0f));
        Instances.setBool(character, ATTACK_LEFT, player.getMainArm() == HumanoidArm.LEFT);
        Instances.setNum(character, SWIM_AMOUNT, player.getSwimAmount(1.0f));
        Instances.setBool(character, RIDING, player.isPassenger());
        Instances.setBool(character, FLYING, player.isFallFlying());
        Instances.setBool(character, IN_WATER, player.isInWater());

        Instances.setNum(character, FLYING_TIME, player.getFallFlyingTicks());
        Instances.setNum(character, FLYING_YAW, flyingYaw(player));
        Instances.setNum(character, DEATH_TIME, player.deathTime);
        Instances.setBool(character, SLEEPING, player.isSleeping());
        Instances.setNum(character, BED_YAW, Math.toRadians(bedAngle(player)));
        Instances.setBool(character, CRAWLING, player.isVisuallySwimming());
        Instances.setBool(character, SPINNING, player.isAutoSpinAttack());
        Instances.setBool(character, FROZEN, player.isFullyFrozen());
        // the same condition the model washes a body red on: still bleeding, or already down
        Instances.setBool(character, HURT, player.hurtTime > 0 || player.deathTime > 0);
    }

    // how far the body is banking under a wing: the angle between where it is going and where it
    // is looking, signed by which side it is turning toward
    private static double flyingYaw(Player player) {
        // net.minecraft.world.phys.Vec3 against ours, the one clash 20.1 allows for
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        net.minecraft.world.phys.Vec3 move = player.getDeltaMovement();
        if (move.horizontalDistanceSqr() <= 1.0E-5 || look.horizontalDistanceSqr() <= 1.0E-5) {
            return 0;
        }
        double along = move.horizontal().normalize().dot(look.horizontal().normalize());
        double side = move.x * look.z - move.z * look.x;
        return Math.signum(side) * Math.acos(Math.min(1.0, Math.abs(along)));
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
            }
        }
    }
}
