package com.meekdev.moud.script.api;

public interface SpawnRef {

    boolean autoSpawn();

    void autoSpawn(boolean on);

    double respawnTime();

    void respawnTime(double seconds);
}
