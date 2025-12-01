package com.moud.client.particle;

import com.moud.api.particle.Billboarding;
import com.moud.api.particle.CollisionMode;
import com.moud.api.particle.ColorKeyframe;
import com.moud.api.particle.Ease;
import com.moud.api.particle.FrameAnimation;
import com.moud.api.particle.LightSettings;
import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleMath;
import com.moud.api.particle.RenderType;
import com.moud.api.particle.ScalarKeyframe;
import com.moud.api.particle.SortHint;
import com.moud.api.particle.UVRegion;
import com.moud.api.particle.Vector3f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;
import java.util.Map;

final class ParticleInstance {
    private static final float GRAVITY = 9.81f;
    private static final float BOUNCE_DAMPING = 0.5f;
    private static final float SLIDE_DAMPING = 0.2f;

    final int index;
    boolean alive;

    String texture;
    RenderType renderType;
    Billboarding billboarding;
    CollisionMode collisionMode;
    SortHint sortHint;
    float x;
    float y;
    float z;
    float prevX;
    float prevY;
    float prevZ;
    float vx;
    float vy;
    float vz;
    float ax;
    float ay;
    float az;
    float drag;
    float gravityMultiplier;
    float age;
    float lifetime;
    List<ScalarKeyframe> sizeOverLife;
    List<ScalarKeyframe> rotationOverLife;
    List<ColorKeyframe> colorOverLife;
    List<ScalarKeyframe> alphaOverLife;
    UVRegion uvRegion;
    FrameAnimation frameAnimation;
    List<String> behaviors;
    Map<String, Object> behaviorPayload;
    LightSettings light;

    // computed per tick
    float size;
    float rotation;
    ParticleMath.ColorSample colorSample = new ParticleMath.ColorSample(1f, 1f, 1f, 1f);
    float alpha;
    int currentFrame;

    ParticleInstance(int index) {
        this.index = index;
    }

    void reset(ParticleDescriptor descriptor) {
        this.texture = descriptor.texture();
        this.renderType = descriptor.renderType();
        this.billboarding = descriptor.billboarding();
        this.collisionMode = descriptor.collisionMode();
        this.sortHint = descriptor.sortHint();

        Vector3f pos = descriptor.position();
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;

        Vector3f vel = descriptor.velocity();
        this.vx = vel.x();
        this.vy = vel.y();
        this.vz = vel.z();

        Vector3f acc = descriptor.acceleration();
        this.ax = acc.x();
        this.ay = acc.y();
        this.az = acc.z();

        this.drag = descriptor.drag();
        this.gravityMultiplier = descriptor.gravityMultiplier();
        this.age = 0f;
        this.lifetime = descriptor.lifetimeSeconds();

        this.sizeOverLife = descriptor.sizeOverLife();
        this.rotationOverLife = descriptor.rotationOverLife();
        this.colorOverLife = descriptor.colorOverLife();
        this.alphaOverLife = descriptor.alphaOverLife();
        this.uvRegion = descriptor.uvRegion();
        this.frameAnimation = descriptor.frameAnimation();
        this.behaviors = descriptor.behaviors();
        this.behaviorPayload = descriptor.behaviorPayload();
        this.light = descriptor.light();

        this.size = 1f;
        this.rotation = 0f;
        this.alpha = 1f;
        this.currentFrame = 0;
    }

    boolean step(float dt, ClientWorld world) {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        age += dt;
        if (age >= lifetime) {
            return false;
        }

        // integrate velocity
        vx += ax * dt;
        vy += (ay - (GRAVITY * gravityMultiplier)) * dt;
        vz += az * dt;

        if (drag != 0f) {
            float damping = Math.max(0f, 1f - drag * dt);
            vx *= damping;
            vy *= damping;
            vz *= damping;
        }

        double dx = vx * dt;
        double dy = vy * dt;
        double dz = vz * dt;

        boolean slideCeilingHit = false;

        if (collisionMode != CollisionMode.NONE && world != null) {
            float radius = Math.max(0.05f, size * 0.5f);
            Box box = new Box(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);

            dx = resolveAxis(world, box, dx, Direction.Axis.X, false);
            box = box.offset(dx, 0, 0);
            vx = (float) (dx / dt);

            dy = resolveAxis(world, box, dy, Direction.Axis.Y, true);
            if (dy == 0.0 && collisionMode == CollisionMode.SLIDE) {
                slideCeilingHit = true;
            }
            box = box.offset(0, dy, 0);
            vy = (float) (dy / dt);

            dz = resolveAxis(world, box, dz, Direction.Axis.Z, false);
            box = box.offset(0, 0, dz);
            vz = (float) (dz / dt);
        }

        x += dx;
        y += dy;
        z += dz;

        if (slideCeilingHit && collisionMode == CollisionMode.SLIDE) {
            // If we hit a ceiling, push outward to let particles ooze from under blocks before rising.
            double angle = Math.random() * Math.PI * 2.0;
            float push = 0.6f;
            vx += (float) Math.cos(angle) * push;
            vz += (float) Math.sin(angle) * push;
            vy = Math.max(vy, 0.08f);
        }

        float t = lifetime > 0f ? Math.max(0f, Math.min(1f, age / lifetime)) : 0f;
        size = ParticleMath.evaluateScalarRamp(sizeOverLife, t);
        rotation = ParticleMath.evaluateScalarRamp(rotationOverLife, t);
        colorSample = ParticleMath.evaluateColorRamp(colorOverLife, t);
        float alphaRamp = ParticleMath.evaluateScalarRamp(alphaOverLife, t);
        alpha = alphaRamp > 0f ? alphaRamp : colorSample.a();
        currentFrame = resolveFrame(t);
        return true;
    }

    private int resolveFrame(float t) {
        if (frameAnimation == null || frameAnimation.frames() <= 1) {
            return 0;
        }
        int totalFrames = frameAnimation.frames();
        float fps = frameAnimation.fps();
        if (fps <= 0f) {
            return frameAnimation.startFrame();
        }
        float totalTime = lifetime > 0f ? lifetime : 1f;
        float animationTime = t * totalTime;
        int frameIndex = frameAnimation.startFrame() + (int) (animationTime * fps);
        if (frameAnimation.loop()) {
            frameIndex = frameAnimation.pingPong()
                    ? pingPong(frameIndex, totalFrames)
                    : frameIndex % totalFrames;
        } else {
            frameIndex = Math.min(frameIndex, totalFrames - 1);
        }
        return frameIndex;
    }

    private int pingPong(int frameIndex, int totalFrames) {
        int cycle = totalFrames * 2 - 2;
        int mod = Math.floorMod(frameIndex, cycle);
        return mod < totalFrames ? mod : cycle - mod;
    }

    private double resolveAxis(ClientWorld world, Box box, double delta, Direction.Axis axis, boolean isCeilingCheck) {
        if (delta == 0.0) {
            return 0.0;
        }
        double dx = axis == Direction.Axis.X ? delta : 0.0;
        double dy = axis == Direction.Axis.Y ? delta : 0.0;
        double dz = axis == Direction.Axis.Z ? delta : 0.0;
        Box target = box.offset(dx, dy, dz);
        Iterable<VoxelShape> shapes = world.getBlockCollisions(null, target);
        if (!shapes.iterator().hasNext()) {
            return delta;
        }

        switch (collisionMode) {
        case KILL -> {
            alive = false;
            return 0.0;
        }
        case BOUNCE -> {
            double damp = BOUNCE_DAMPING;
            return -delta * damp;
        }
        case SLIDE -> {
            double damp = 1.0 - SLIDE_DAMPING;
            return 0.0 * damp;
        }
        case STICK -> {
            ax = ay = az = 0f;
            return 0.0;
        }
        case DAMP -> {
            return delta * 0.5;
        }
        case NONE -> {
            return delta;
        }
        }
        return delta;
    }
}
