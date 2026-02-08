package com.moud.client.particle;

final class ParticlePool {
    private final ParticleInstance[] particles;
    private final int[] freeStack;
    private int top;

    ParticlePool(int capacity) {
        this.particles = new ParticleInstance[capacity];
        this.freeStack = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            particles[i] = new ParticleInstance(i);
            freeStack[i] = i;
        }
        this.top = capacity;
    }

    ParticleInstance borrow() {
        if (top == 0) {
            return null;
        }
        int idx = freeStack[--top];
        ParticleInstance instance = particles[idx];
        instance.alive = true;
        return instance;
    }

    void release(ParticleInstance instance) {
        if (instance == null || !instance.alive) {
            return;
        }
        instance.alive = false;
        freeStack[top++] = instance.index;
    }

    ParticleInstance get(int index) {
        return particles[index];
    }

    ParticleInstance[] backing() {
        return particles;
    }
}
