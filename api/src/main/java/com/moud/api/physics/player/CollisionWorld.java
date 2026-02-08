package com.moud.api.physics.player;

import com.moud.api.collision.AABB;

import java.util.List;

public interface CollisionWorld {
    List<AABB> getCollisions(AABB entityBox);
}

