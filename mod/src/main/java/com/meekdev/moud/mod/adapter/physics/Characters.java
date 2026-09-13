package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.physics.Physics;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Appearance;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.CollisionGroups;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Humanoid;
import com.meekdev.moud.core.instance.Wings;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import com.meekdev.moud.core.instance.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ElytraAnimationState;
import com.meekdev.moud.core.instance.Armour;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.core.Direction;
import com.meekdev.moud.core.instance.Cape;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.world.entity.player.PlayerModelType;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelTracking;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Transforms;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

public final class Characters {

    private static final double TICKS = 20.0;

    private static final PropertyDef CFRAME = Classes.CHARACTER.property("cframe");
    private static final PropertyDef OWNER = Classes.CHARACTER.property("owner");
    private static final PropertyDef COLLISION_GROUP = Classes.CHARACTER.property("collisionGroup");
    private static final PropertyDef LOOK_PITCH = Classes.CHARACTER.property("lookPitch");
    private static final PropertyDef LOOK_YAW = Classes.CHARACTER.property("lookYaw");
    private static final PropertyDef MOVE_DISTANCE = Classes.CHARACTER.property("moveDistance");
    private static final PropertyDef MOVE_SPEED = Classes.CHARACTER.property("moveSpeed");
    private static final PropertyDef CROUCHING = Classes.CHARACTER.property("crouching");
    private static final PropertyDef VELOCITY = Classes.CHARACTER.property("velocity");
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
    private static final PropertyDef MAIN_LEFT = Classes.CHARACTER.property("mainLeft");
    private static final PropertyDef RIGHT_ARM_POSE = Classes.CHARACTER.property("rightArmPose");
    private static final PropertyDef LEFT_ARM_POSE = Classes.CHARACTER.property("leftArmPose");
    private static final PropertyDef RIGHT_ITEM = Classes.CHARACTER.property("rightItem");
    private static final PropertyDef LEFT_ITEM = Classes.CHARACTER.property("leftItem");
    private static final PropertyDef USING_ITEM = Classes.CHARACTER.property("usingItem");
    private static final PropertyDef USE_LEFT_HAND = Classes.CHARACTER.property("useLeftHand");
    private static final PropertyDef CHARGE = Classes.CHARACTER.property("chargeProgress");
    private static final PropertyDef ARMOUR_HEAD = Classes.ARMOUR.property("head");
    private static final PropertyDef ARMOUR_CHEST = Classes.ARMOUR.property("chest");
    private static final PropertyDef ARMOUR_LEGS = Classes.ARMOUR.property("legs");
    private static final PropertyDef ARMOUR_FEET = Classes.ARMOUR.property("feet");
    private static final PropertyDef ARMOUR_HAT = Classes.ARMOUR.property("hat");
    private static final PropertyDef ARMOUR_HAT_LAYERED = Classes.ARMOUR.property("hatLayered");
    private static final PropertyDef WORN = Classes.WINGS.property("worn");
    private static final PropertyDef WING_X = Classes.WINGS.property("x");
    private static final PropertyDef WING_Y = Classes.WINGS.property("y");
    private static final PropertyDef WING_Z = Classes.WINGS.property("z");
    private static final PropertyDef CRAWLING = Classes.CHARACTER.property("crawling");
    private static final PropertyDef SPINNING = Classes.CHARACTER.property("spinning");
    private static final PropertyDef FROZEN = Classes.CHARACTER.property("frozen");
    private static final PropertyDef HURT = Classes.CHARACTER.property("hurt");
    private static final PropertyDef EARS = Classes.APPEARANCE.property("ears");

    private final Map<UUID, Integer> bound = new HashMap<>();

    private final Colliders boxes;

    Characters(Colliders boxes) {
        this.boxes = boxes;
    }

    private CollisionGroups groups() {
        return boxes.groups();
    }

    public static MovementProfile currentProfile(LivingEntity entity) {
        return Physics.getProfile(entity).orElse(null);
    }

    public MovementProfile profileOf(Character character) {
        Humanoid living = Rig.humanoid(character);
        if (living == null) return MovementProfile.builder().build();
        return MovementProfile.builder()
                .gravityScale(living.gravityScale)
                .maxGroundSpeed(living.walkSpeed / TICKS)
                .sprintMultiplier(living.sprintMultiplier)
                .sneakMultiplier(living.sneakMultiplier)
                .maxAirSpeed(living.airSpeed / TICKS)
                .groundAcceleration(living.groundAcceleration / (TICKS * TICKS))
                .groundDeceleration(living.groundDeceleration / (TICKS * TICKS))
                .airAcceleration(living.airAcceleration / (TICKS * TICKS))
                .slideAcceleration(living.slideAcceleration / (TICKS * TICKS))
                .jumpPower(living.jumpPower / TICKS)
                .airDrag(Math.pow(living.airDrag, 1.0 / TICKS))
                .fallDrag(Math.pow(living.fallDrag, 1.0 / TICKS))
                .stepHeight(living.stepHeight)
                .slideThresholdDegrees(living.slopeLimit)
                .coyoteTicks((int) Math.round(living.coyoteTime * TICKS))
                .jumpBufferTicks((int) Math.round(living.jumpBuffer * TICKS))
                .followSlopes(living.followSlopes)
                .moverShape(character.radius, character.height)
                .collisionFilter(groups().category(character.collisionGroup),
                        groups().mask(character.collisionGroup))
                .build();
    }

    public static void place(Character character, Vector3 position, double yawDegrees) {
        double yaw = Math.PI - Math.toRadians(yawDegrees);
        Instances.setObj(character, CFRAME, Transforms.localFor(character,
                new CFrame(position, Quat.euler(0, yaw, 0))));
    }

    public void follow(MinecraftServer server, @Nullable InstanceTree tree, SubLevels shapes) {
        if (tree == null) return;
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(tree.byId(entry.getValue()) instanceof Character character)) {
                continue;
            }
            ride(character, player, tree, shapes);
            drive(character, player);
        }
    }

    public void ride(Character character, Entity player, InstanceTree tree, SubLevels shapes) {
        Instance want = tree.root();
        SubLevelEntity deck = SubLevelTracking.of(player);
        if (deck != null && !deck.isRemoved()) {
            int id = shapes.instanceOf(deck);
            if (tree.byId(id) instanceof Part part) want = part;
        }
        if (want == null || want == character || character.parent() == want) return;

        CFrame world = Transforms.world(character);
        Instances.reparent(character, want);
        Instances.setObj(character, CFRAME, Transforms.localFor(character, world));
    }

    private static final PropertyDef SLIM = Classes.APPEARANCE.property("slim");
    private static final PropertyDef CAPE_FLAP = Classes.CAPE.property("flap");
    private static final PropertyDef CAPE_LEAN = Classes.CAPE.property("lean");
    private static final PropertyDef CAPE_SWAY = Classes.CAPE.property("sway");

    public static void dress(Character character, AbstractClientPlayer wearer, float partialTick) {
        if (!(character.child("torso") instanceof Instance torso)) return;
        if (!(torso.child(Rig.CAPE) instanceof Cape cape)) return;

        ClientAvatarState state = wearer.avatarState();
        double deltaX = state.getInterpolatedCloakX(partialTick)
                - Mth.lerp(partialTick, wearer.xo, wearer.getX());
        double deltaY = state.getInterpolatedCloakY(partialTick)
                - Mth.lerp(partialTick, wearer.yo, wearer.getY());
        double deltaZ = state.getInterpolatedCloakZ(partialTick)
                - Mth.lerp(partialTick, wearer.zo, wearer.getZ());

        float facing = Mth.rotLerp(partialTick, wearer.yBodyRotO, wearer.yBodyRot);
        double forwardX = Mth.sin(facing * (float) (Math.PI / 180.0));
        double forwardZ = -Mth.cos(facing * (float) (Math.PI / 180.0));

        double wing = Mth.clamp(character.flyingTime * character.flyingTime / 100.0, 0.0, 1.0);

        double flap = Mth.clamp(deltaY * 10.0, -6.0, 32.0)
                + Math.sin(state.getInterpolatedWalkDistance(partialTick) * 6.0) * 32.0
                        * state.getInterpolatedBob(partialTick);
        double lean = Mth.clamp((deltaX * forwardX + deltaZ * forwardZ) * 100.0 * (1.0 - wing),
                0.0, 150.0);
        double sway = Mth.clamp((deltaX * forwardZ - deltaZ * forwardX) * 100.0, -20.0, 20.0);

        Instances.setNum(cape, CAPE_FLAP, flap);
        Instances.setNum(cape, CAPE_LEAN, lean);
        Instances.setNum(cape, CAPE_SWAY, sway);
    }

    public static void fit(Character character, AbstractClientPlayer wearer) {
        if (!(Rig.appearance(character) instanceof Appearance look)) return;
        Instances.setBool(look, SLIM, wearer.getSkin().model() == PlayerModelType.SLIM);
    }

    public static void drive(Character character, Player player) {
        if (Math.abs(player.getBbWidth() - character.radius * 2) > 1e-4
                || Math.abs(player.getBbHeight() - character.height) > 1e-4) {
            player.refreshDimensions();
        }
        double heading = player.isSleeping() ? 180.0f - bedAngle(player) : player.yBodyRot;
        place(character, new Vector3(player.getX(), player.getY(), player.getZ()), heading);
        animation(character, player);
    }

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

    private static void animation(Character character, Player player) {
        double relative = Math.toRadians(Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot));
        Instances.setNum(character, LOOK_PITCH, Math.toRadians(player.getXRot()));
        Instances.setNum(character, LOOK_YAW, relative);
        Instances.setNum(character, MOVE_DISTANCE, player.walkAnimation.position());
        Instances.setNum(character, MOVE_SPEED, Math.min(1.0, player.walkAnimation.speed()));
        Instances.setBool(character, CROUCHING, player.isCrouching());

        Instances.setNum(character, ATTACK_TIME, player.getAttackAnim(1.0f));
        Instances.setBool(character, ATTACK_LEFT, player.getMainArm() == HumanoidArm.LEFT);
        Instances.setNum(character, SWIM_AMOUNT, player.getSwimAmount(1.0f));
        Instances.setBool(character, RIDING, player.isPassenger());
        Instances.setBool(character, FLYING, player.isFallFlying());
        Instances.setBool(character, IN_WATER, player.isInWater());
        Vec3 motion = player.getDeltaMovement();
        Instances.setObj(character, VELOCITY, new Vector3(motion.x * 20, motion.y * 20, motion.z * 20));

        Instances.setNum(character, FLYING_TIME, player.getFallFlyingTicks());
        Instances.setNum(character, FLYING_YAW, flyingYaw(player));
        Instances.setNum(character, DEATH_TIME, player.deathTime);
        Instances.setBool(character, SLEEPING, player.isSleeping());
        Instances.setNum(character, BED_YAW, Math.toRadians(bedAngle(player)));
        Instances.setBool(character, CRAWLING, player.isVisuallySwimming());
        Instances.setBool(character, SPINNING, player.isAutoSpinAttack());
        Instances.setBool(character, FROZEN, player.isFullyFrozen());
        Instances.setBool(character, HURT, player.hurtTime > 0 || player.deathTime > 0);
        if (Rig.appearance(character) instanceof Appearance look) {
            Instances.setBool(look, EARS, "deadmau5".equals(player.getGameProfile().name()));
        }

        boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
        Instances.setBool(character, MAIN_LEFT, mainLeft);
        Instances.setBool(character, USING_ITEM, player.isUsingItem());
        Instances.setBool(character, USE_LEFT_HAND,
                (player.getUsedItemHand() == InteractionHand.OFF_HAND) != mainLeft);
        Instances.setObj(character, RIGHT_ARM_POSE, armPose(player, HumanoidArm.RIGHT));
        Instances.setObj(character, LEFT_ARM_POSE, armPose(player, HumanoidArm.LEFT));
        Instances.setObj(character, RIGHT_ITEM, itemId(handOf(player, HumanoidArm.RIGHT)));
        Instances.setObj(character, LEFT_ITEM, itemId(handOf(player, HumanoidArm.LEFT)));
        Instances.setNum(character, CHARGE, charge(player));

        if (Rig.armour(character) instanceof Armour worn) {
            Instances.setObj(worn, ARMOUR_HEAD, plate(player, EquipmentSlot.HEAD, "humanoid"));
            Instances.setObj(worn, ARMOUR_CHEST, plate(player, EquipmentSlot.CHEST, "humanoid"));
            Instances.setObj(worn, ARMOUR_LEGS,
                    plate(player, EquipmentSlot.LEGS, "humanoid_leggings"));
            Instances.setObj(worn, ARMOUR_FEET, plate(player, EquipmentSlot.FEET, "humanoid"));
            head(worn, player);
        }

        if (Rig.wings(character) instanceof Wings pair) {
            Instances.setBool(pair, WORN,
                    player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA));
            ElytraAnimationState held = player.elytraAnimationState;
            Instances.setNum(pair, WING_X, held.getRotX(1.0f));
            Instances.setNum(pair, WING_Y, held.getRotY(1.0f));
            Instances.setNum(pair, WING_Z, held.getRotZ(1.0f));
        }
    }

    private static ArmPose armPose(Player player, HumanoidArm arm) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        ArmPose mainPose = poseOf(player, main, InteractionHand.MAIN_HAND);
        ArmPose offPose = poseOf(player, off, InteractionHand.OFF_HAND);
        if (mainPose.twoHanded()) offPose = off.isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        return player.getMainArm() == arm ? mainPose : offPose;
    }

    public static ItemStack handOf(Player player, HumanoidArm arm) {
        return player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    private static String itemId(ItemStack held) {
        return held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
    }

    private static ArmPose poseOf(Player player, ItemStack held, InteractionHand hand) {
        if (held.isEmpty()) return ArmPose.EMPTY;
        if (!player.swinging && held.is(Items.CROSSBOW) && CrossbowItem.isCharged(held)) {
            return ArmPose.CROSSBOW_HOLD;
        }
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            return switch (held.getUseAnimation()) {
                case BLOCK -> ArmPose.BLOCK;
                case BOW -> ArmPose.BOW;
                case TRIDENT -> ArmPose.TRIDENT;
                case CROSSBOW -> ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS -> ArmPose.SPYGLASS;
                case TOOT_HORN -> ArmPose.HORN;
                case BRUSH -> ArmPose.BRUSH;
                case SPEAR -> ArmPose.SPEAR;
                default -> ArmPose.ITEM;
            };
        }
        return ArmPose.ITEM;
    }

    private static double charge(Player player) {
        ItemStack held = player.getUseItem();
        if (held.isEmpty() || !held.is(Items.CROSSBOW)) return 0;
        double max = CrossbowItem.getChargeDuration(held, player);
        if (max <= 0) return 0;
        double used = held.getUseDuration(player) - player.getUseItemRemainingTicks();
        return Math.max(0, Math.min(1, used / max));
    }

    private static void head(Armour worn, Player player) {
        ItemStack hat = player.getItemBySlot(EquipmentSlot.HEAD);
        SkullBlock.Type type = hat.getItem() instanceof BlockItem block
                && block.getBlock() instanceof AbstractSkullBlock skull ? skull.getType() : null;

        String texture = "";
        boolean layered = false;
        if (type instanceof SkullBlock.Types kind) {
            switch (kind) {
                case SKELETON -> texture = "minecraft:textures/entity/skeleton/skeleton.png";
                case WITHER_SKELETON ->
                        texture = "minecraft:textures/entity/skeleton/wither_skeleton.png";
                case CREEPER -> texture = "minecraft:textures/entity/creeper/creeper.png";
                case ZOMBIE -> {
                    texture = "minecraft:textures/entity/zombie/zombie.png";
                    layered = true;
                }
                case PLAYER -> {
                    texture = "minecraft:textures/entity/player/wide/steve.png";
                    layered = true;
                }
                default -> { }
            }
        }
        Instances.setObj(worn, ARMOUR_HAT, texture);
        Instances.setBool(worn, ARMOUR_HAT_LAYERED, layered);
    }

    private static String plate(Player player, EquipmentSlot slot, String shape) {
        ItemStack worn = player.getItemBySlot(slot);
        Equippable kit = worn.get(DataComponents.EQUIPPABLE);
        if (kit == null || kit.assetId().isEmpty()) return "";
        Identifier asset = kit.assetId().get().identifier();
        return asset.withPath(name ->
                "textures/entity/equipment/" + shape + "/" + name + ".png").toString();
    }

    private static double flyingYaw(Player player) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 move = player.getDeltaMovement();
        if (move.horizontalDistanceSqr() <= 1.0E-5 || look.horizontalDistanceSqr() <= 1.0E-5) {
            return 0;
        }
        double along = move.horizontal().normalize().dot(look.horizontal().normalize());
        double side = move.x * look.z - move.z * look.x;
        return Math.signum(side) * Math.acos(Math.min(1.0, Math.abs(along)));
    }

    public void bind(ServerPlayer player, Character character) {
        bound.put(player.getUUID(), character.id());
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

    public void apply(InstanceTree source, Change change, MinecraftServer server) {
        if (boxes.groupsChanged()) {
            for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && source.byId(entry.getValue()) instanceof Character character) {
                    Physics.setProfile(player, profileOf(character));
                }
            }
            return;
        }
        if (change instanceof Change.Wrote wrote && source.byId(wrote.id()) instanceof Character body
                && wrote.property() == COLLISION_GROUP.index()) {
            push(body, server);
            return;
        }
        if (!(change instanceof Change.Wrote wrote)) return;
        if (!(source.byId(wrote.id()) instanceof Humanoid living)
                || !(living.parent() instanceof Character character)) {
            return;
        }
        push(character, server);
    }

    private void push(Character character, MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : bound.entrySet()) {
            if (entry.getValue() != character.id()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) Physics.setProfile(player, profileOf(character));
        }
    }
}
