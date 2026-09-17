package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.Blasts;
import com.meekdev.moud.core.effect.Explosion;
import com.meekdev.moud.core.effect.ExplosionType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.physics.PartBodies;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

public final class Explosions {

    private static final double LINGER = 0.5;
    private static final double CRATER_REACH = 24;
    private static final float TOUGH = 50;

    private static final Map<Integer, Double> FIRED = new HashMap<>();

    private Explosions() {}

    static void stopped() {
        FIRED.clear();
    }

    static void tick(MinecraftServer server) {
        InstanceTree tree = ServerScene.tree();
        if (tree == null) return;
        double now = System.nanoTime() / 1.0e9;
        for (Explosion explosion : List.copyOf(tree.ofClass(Classes.EXPLOSION))) {
            if (explosion.parent() == null || Instance.outOfWorld(explosion)) continue;
            if (FIRED.containsKey(explosion.id())) continue;
            FIRED.put(explosion.id(), now);
            blast(server, tree, explosion);
        }
        for (Iterator<Map.Entry<Integer, Double>> it = FIRED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Double> fired = it.next();
            Instance instance = tree.byId(fired.getKey());
            if (instance == null) {
                it.remove();
                continue;
            }
            if (now - fired.getValue() < LINGER) continue;
            Instances.destroy(instance);
            it.remove();
        }
    }

    private static void blast(MinecraftServer server, InstanceTree tree, Explosion explosion) {
        Vector3 centre = explosion.position;
        double radius = explosion.blastRadius;
        if (radius <= 0) return;
        show(server, explosion, centre);
        for (Blasts.Caught caught : Blasts.caught(tree, centre, radius, Transforms::world)) {
            Part part = caught.part();
            explosion.hit.fire(new Object[] {part, caught.distance()});
            if (part.anchored || !part.collides) continue;
            Vector3 push = Blasts.push(centre, Transforms.world(part).position(), explosion.blastPressure, radius);
            if (push.lengthSq() <= 0) continue;
            PartBodies.INSTANCE.applyImpulse(part, push.mul(PartBodies.INSTANCE.mass(part)), Transforms.world(part).position());
        }
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (Instance.outOfWorld(body)) continue;
            Vector3 at = Transforms.world(body).position();
            double distance = at.distance(centre);
            if (distance > radius) continue;
            Vector3 push = Blasts.push(centre, at, explosion.blastPressure, radius);
            if (!body.owner.isEmpty() && push.lengthSq() > 0) ServerPush.INSTANCE.push(body, push, false);
            if (!Blasts.breaksJoints(distance, radius, explosion.destroyJointRadiusPercent)) continue;
            Humanoid living = Rig.humanoid(body);
            if (living != null) Humanoids.takeDamage(living, living.health);
        }
        if (explosion.explosionType == ExplosionType.CRATERS) crater(server.overworld(), centre, radius);
    }

    private static void show(MinecraftServer server, Explosion explosion, Vector3 centre) {
        if (!explosion.visible) return;
        ServerLevel level = server.overworld();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x(), centre.y(), centre.z(), 1, 0, 0, 0, 0);
        level.playSound(null, centre.x(), centre.y(), centre.z(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                (float) Math.clamp(explosion.blastRadius / 4, 0.5, 4), 1);
    }

    private static void crater(ServerLevel level, Vector3 centre, double radius) {
        double reach = Math.min(radius, CRATER_REACH);
        int low = (int) Math.floor(-reach);
        int high = (int) Math.ceil(reach);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = low; x <= high; x++) {
            for (int y = low; y <= high; y++) {
                for (int z = low; z <= high; z++) {
                    if (x * x + y * y + z * z > reach * reach) continue;
                    at.set((int) Math.floor(centre.x()) + x, (int) Math.floor(centre.y()) + y, (int) Math.floor(centre.z()) + z);
                    BlockState state = level.getBlockState(at);
                    if (state.isAir() || state.getBlock().getExplosionResistance() > TOUGH) continue;
                    level.destroyBlock(at, false);
                }
            }
        }
    }
}
