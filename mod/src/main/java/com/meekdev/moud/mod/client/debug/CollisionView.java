package com.meekdev.moud.mod.client.debug;

import com.meekdev.bkun.collision.BoxCollider;
import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.physics.Physics;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.physics.Colliders;
import com.meekdev.moud.mod.adapter.physics.SubLevels;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.glfw.GLFW;
import com.meekdev.moud.mod.client.ClientScene;

public final class CollisionView {

    private static final double RANGE = 40;
    private static final int BLOCK_RANGE = 5;
    private static final double LIFE = 0.08;

    private static final Color GREEN = new Color(0.2f, 1f, 0.3f, 1);
    private static final Color CYAN = new Color(0.2f, 0.9f, 1f, 1);
    private static final Color ORANGE = new Color(1f, 0.55f, 0.1f, 1);
    private static final Color GREY = new Color(0.55f, 0.55f, 0.55f, 1);
    private static final Color WHITE = new Color(1f, 1f, 1f, 0.6f);
    private static final Color YELLOW = new Color(1f, 0.9f, 0.2f, 1);

    private static KeyMapping toggle;
    private static boolean on;

    private CollisionView() {}

    public static void register(KeyMapping.Category category) {
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moud.collisions",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
    }

    public static void tick() {
        while (toggle != null && toggle.consumeClick()) {
            on = !on;
            if (!on) ClientDebug.INSTANCE.unwatch("collisions");
        }
        Minecraft client = Minecraft.getInstance();
        InstanceTree tree = ClientScene.tree();
        if (!on || tree == null || client.player == null || client.level == null) return;
        ClientDebug debug = ClientDebug.INSTANCE;
        var eye = client.player.getEyePosition();

        int[] boxes = {0};
        AABB region = new AABB(eye.x - RANGE, eye.y - RANGE, eye.z - RANGE, eye.x + RANGE, eye.y + RANGE, eye.z + RANGE);
        ClientPhysics.boxes().collect(region, collider -> {
            if (!(collider instanceof BoxCollider box)) return;
            AABB b = box.bounds();
            debug.box(CFrame.at((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2),
                    new Vector3(b.getXsize(), b.getYsize(), b.getZsize()), GREEN, LIFE);
            boxes[0]++;
        });

        int turned = 0;
        int ghosts = 0;
        for (Part part : tree.ofClass(Classes.PART)) {
            if (part.parent() instanceof Character || part.parent() != null && part.parent().parent() instanceof Character) continue;
            CFrame frame = Transforms.world(part);
            Vector3 at = frame.position();
            if (Math.abs(at.x() - eye.x) > RANGE || Math.abs(at.y() - eye.y) > RANGE || Math.abs(at.z() - eye.z) > RANGE) continue;
            if (!part.collides) {
                debug.box(frame, part.size, GREY, LIFE);
                ghosts++;
            } else if (!Colliders.isAxisAligned(part)) {
                debug.box(frame, part.size, SubLevels.available() ? CYAN : ORANGE, LIFE);
                turned++;
            }
        }

        int blocks = 0;
        BlockPos centre = client.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BLOCK_RANGE, -BLOCK_RANGE, -BLOCK_RANGE),
                centre.offset(BLOCK_RANGE, BLOCK_RANGE, BLOCK_RANGE))) {
            BlockState state = client.level.getBlockState(pos);
            if (state.isAir()) continue;
            for (AABB b : state.getCollisionShape(client.level, pos, CollisionContext.empty()).toAabbs()) {
                debug.box(CFrame.at(pos.getX() + (b.minX + b.maxX) / 2, pos.getY() + (b.minY + b.maxY) / 2, pos.getZ() + (b.minZ + b.maxZ) / 2),
                        new Vector3(b.getXsize(), b.getYsize(), b.getZsize()), WHITE, LIFE);
                blocks++;
            }
        }

        int bodies = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity.distanceToSqr(eye) > RANGE * RANGE) continue;
            MovementProfile profile = Physics.getProfile(living).orElse(null);
            double radius = profile != null ? profile.moverRadius() : entity.getBbWidth() / 2;
            double height = profile != null ? profile.moverHeight() : entity.getBbHeight();
            capsule(debug, new Vector3(entity.getX(), entity.getY(), entity.getZ()), radius, height);
            bodies++;
        }
        for (Character character : tree.ofClass(Classes.CHARACTER)) {
            if (!character.owner.isEmpty()) continue;
            Vector3 at = Transforms.world(character).position();
            if (Math.abs(at.x() - eye.x) > RANGE || Math.abs(at.z() - eye.z) > RANGE) continue;
            capsule(debug, at, character.radius * character.scale, character.height * character.scale);
            bodies++;
        }

        debug.watch("collisions", "F7  " + boxes[0] + " boxes, " + turned + (SubLevels.available() ? " turned (real)" : " turned (as boxes)")
                + ", " + ghosts + " not colliding, " + blocks + " block boxes, " + bodies + " bodies");
    }

    private static void capsule(ClientDebug debug, Vector3 feet, double radius, double height) {
        int segments = 16;
        double[] rings = {radius, height / 2, height - radius};
        for (double y : rings) {
            for (int i = 0; i < segments; i++) {
                double a = 2 * Math.PI * i / segments, b = 2 * Math.PI * (i + 1) / segments;
                debug.line(feet.add(new Vector3(Math.cos(a) * radius, y, Math.sin(a) * radius)),
                        feet.add(new Vector3(Math.cos(b) * radius, y, Math.sin(b) * radius)), YELLOW, LIFE);
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            Vector3 side = new Vector3(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            debug.line(feet.add(side).add(new Vector3(0, radius, 0)), feet.add(side).add(new Vector3(0, height - radius, 0)), YELLOW, LIFE);
            debug.line(feet.add(side).add(new Vector3(0, radius, 0)), feet.add(new Vector3(0, 0, 0)), YELLOW, LIFE);
            debug.line(feet.add(side).add(new Vector3(0, height - radius, 0)), feet.add(new Vector3(0, height, 0)), YELLOW, LIFE);
        }
    }
}
