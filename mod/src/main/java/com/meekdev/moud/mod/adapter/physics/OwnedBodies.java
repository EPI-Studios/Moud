package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.bkun.sublevel.SubLevelEntity;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.Seat;
import com.meekdev.moud.core.part.Seats;
import com.meekdev.moud.mod.transport.payload.OwnedPosePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class OwnedBodies {

    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");
    private static final PropertyDef VELOCITY = Classes.PART.property("velocity");
    private static final PropertyDef ANGULAR_VELOCITY = Classes.PART.property("angularVelocity");

    private static final Map<Integer, SubLevelEntity> SIMULATING = new HashMap<>();
    private static final Joints JOINTS = new Joints(false);
    private static final double TICKS = 20;
    private static final Set<Part> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<B3Body> DRESSED = Collections.newSetFromMap(new WeakHashMap<>());

    private OwnedBodies() {}

    private static double gravity = Double.NaN;
    private static @Nullable LevelPhysics gravityIn;

    public static void tick(@Nullable InstanceTree tree, @Nullable ClientLevel level, String me, boolean running, double wanted,
                            @Nullable Character own) {
        LevelPhysics physics = level == null ? null : LevelPhysics.peek(level);
        if (physics != null && (wanted != gravity || physics != gravityIn)) {
            gravity = wanted;
            gravityIn = physics;
            physics.world().setGravity(new Vec3(0, -wanted, 0));
        }
        Map<Integer, SubLevelEntity> decks = new HashMap<>();
        List<OwnedPosePayload.Pose> poses = new ArrayList<>();
        if (tree != null && level != null && running && !me.isEmpty()) {
            for (SubLevelEntity deck : SubLevelIndex.in(level)) {
                if (deck.isAlive() && deck.owner() > 0) decks.put(deck.owner(), deck);
            }
            for (Part part : tree.ofClass(Classes.PART)) {
                if (!part.simulatedBy(me) || poses.size() >= OwnedPosePayload.MOST) continue;
                SubLevelEntity deck = decks.get(part.id());
                if (deck == null) continue;
                SIMULATING.put(part.id(), deck);
                boolean starting = !deck.simulatedHere();
                if (starting) deck.simulateHere(true);
                B3Body body = deck.body();
                if (body == null || !body.isValid() || body.type() != B3BodyType.DYNAMIC) continue;
                if (starting) {
                    CFrame start = Transforms.world(part);
                    body.setTransform(BoxFrames.vec(start.position()), BoxFrames.quat(start.rotation()));
                }
                if (DRESSED.add(body)) SubLevels.dressBody(body, part);
                poses.add(read(part, deck, body));
            }
        }
        for (Iterator<Map.Entry<Integer, SubLevelEntity>> it = SIMULATING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, SubLevelEntity> entry = it.next();
            SubLevelEntity deck = entry.getValue();
            boolean kept = tree != null && running && tree.byId(entry.getKey()) instanceof Part part
                    && part.simulatedBy(me) && decks.get(entry.getKey()) == deck;
            if (kept) continue;
            if (deck.isAlive() && deck.simulatedHere()) deck.simulateHere(false);
            it.remove();
        }
        joints(tree, physics, running);
        if (tree != null && running) seat(tree, me, own);
        if (!poses.isEmpty() && ClientPlayNetworking.canSend(OwnedPosePayload.TYPE)) {
            ClientPlayNetworking.send(new OwnedPosePayload(poses));
        }
    }

    private static void joints(@Nullable InstanceTree tree, @Nullable LevelPhysics physics, boolean running) {
        JOINTS.attach(physics);
        if (tree == null || physics == null || !running) {
            JOINTS.clear();
            return;
        }
        Map<Integer, B3Body> mine = new HashMap<>();
        for (Map.Entry<Integer, SubLevelEntity> entry : SIMULATING.entrySet()) {
            B3Body body = entry.getValue().body();
            if (body != null && body.isValid() && body.type() == B3BodyType.DYNAMIC) mine.put(entry.getKey(), body);
        }
        JOINTS.settle(tree, mine::get, null, physics.world(), part -> mine.containsKey(part.id()));
    }

    private static void seat(InstanceTree tree, String me, @Nullable Character own) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (own == null || player == null) return;
        for (Seat seat : tree.ofClass(Classes.SEAT)) {
            if (!seat.simulatedBy(me) || !(seat.occupant instanceof Humanoid living) || living.parent() != own) continue;
            Vector3 at = Seats.frame(seat).position();
            player.setPos(at.x(), at.y(), at.z());
            player.setDeltaMovement(seat.velocity.x() / TICKS, seat.velocity.y() / TICKS, seat.velocity.z() / TICKS);
            return;
        }
    }

    public static void frame(@Nullable InstanceTree tree, Motion motion) {
        for (Map.Entry<Integer, SubLevelEntity> entry : SIMULATING.entrySet()) {
            B3Body body = entry.getValue().body();
            if (tree == null || !(tree.byId(entry.getKey()) instanceof Part part)) continue;
            if (body == null || !body.isValid() || body.type() != B3BodyType.DYNAMIC) {
                motion.unlive(part);
                continue;
            }
            motion.live(part, Transforms.localFor(part, BoxFrames.of(body)));
            LIVE.add(part);
        }
        LIVE.removeIf(part -> {
            if (part.isAlive() && SIMULATING.containsKey(part.id())) return false;
            motion.unlive(part);
            return true;
        });
    }

    private static OwnedPosePayload.Pose read(Part part, SubLevelEntity deck, B3Body body) {
        CFrame world = BoxFrames.of(body);
        Vector3 speed = BoxFrames.vector(body.linearVelocity());
        Vector3 spin = BoxFrames.vector(body.angularVelocity());
        Instances.setObj(part, CFRAME, Transforms.localFor(part, world));
        Instances.setObj(part, VELOCITY, speed);
        Instances.setObj(part, ANGULAR_VELOCITY, spin);
        Vector3 at = world.position();
        Quat r = world.rotation();
        deck.drivePose(at.x(), at.y(), at.z(), new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
        return new OwnedPosePayload.Pose(part.id(), at.x(), at.y(), at.z(),
                (float) r.x(), (float) r.y(), (float) r.z(), (float) r.w(),
                (float) speed.x(), (float) speed.y(), (float) speed.z(),
                (float) spin.x(), (float) spin.y(), (float) spin.z());
    }
}
