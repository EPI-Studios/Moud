package com.moud.client.particle;

import com.moud.api.particle.ParticleDescriptor;
import net.minecraft.client.world.ClientWorld;

import java.util.List;

public final class ParticleSystem {
    private final ParticlePool pool;
    private final int[] activeIndices;
    private int activeCount = 0;
    private final int capacity;

    public ParticleSystem(int capacity) {
        this.capacity = capacity;
        this.pool = new ParticlePool(capacity);
        this.activeIndices = new int[capacity];
    }

    public void spawnBatch(List<ParticleDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        for (ParticleDescriptor descriptor : descriptors) {
            spawn(descriptor);
        }
    }

    public void spawn(ParticleDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        ParticleInstance instance = pool.borrow();
        if (instance == null) {
            return; // pool exhausted; drop silently
        }
        instance.reset(descriptor);
        activeIndices[activeCount++] = instance.index;
    }

    public void tick(float dt, ClientWorld world) {
        for (int i = activeCount - 1; i >= 0; i--) {
            ParticleInstance particle = pool.get(activeIndices[i]);
            if (particle == null || !particle.alive) {
                removeAt(i);
                continue;
            }
            boolean alive = particle.step(dt, world);
            if (!alive || !particle.alive) {
                pool.release(particle);
                removeAt(i);
            }
        }
    }

    public ParticleInstance[] getParticles() {
        return pool.backing();
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getCapacity() {
        return capacity;
    }

    int[] getActiveIndices() {
        return activeIndices;
    }

    private void removeAt(int idx) {
        activeIndices[idx] = activeIndices[activeCount - 1];
        activeCount--;
    }
}
