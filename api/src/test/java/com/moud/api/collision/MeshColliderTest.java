package com.moud.api.collision;

import com.moud.api.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshColliderTest {

    @Test
    void clipsPassThroughMovementAgainstWall() {
        float[] vertices = new float[]{
                0.35f, 0.0f, -1.0f,
                0.35f, 0.0f, 1.0f,
                0.35f, 2.0f, 1.0f,
                0.35f, 2.0f, -1.0f
        };
        int[] indices = new int[]{
                0, 1, 2,
                0, 2, 3
        };
        CollisionMesh mesh = new CollisionMesh(vertices, indices);

        AABB box = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
        Vector3 movement = new Vector3(1.0, 0.0, 0.0);

        MeshCollisionResult result = MeshCollider.collideWithStepUp(box, movement, mesh, 0.6f);

        assertTrue(result.allowedMovement().x <= 0.051, "Expected wall to clip X movement even if end position clears the plane");
        assertTrue(result.hasCollision(), "Expected collision flag");
    }

    @Test
    void allowsSlidingAlongWall() {
        float[] vertices = new float[]{
                0.35f, 0.0f, -1.0f,
                0.35f, 0.0f, 1.0f,
                0.35f, 2.0f, 1.0f,
                0.35f, 2.0f, -1.0f
        };
        int[] indices = new int[]{0, 1, 2, 0, 2, 3};
        CollisionMesh mesh = new CollisionMesh(vertices, indices);

        double x = 0.049;
        AABB box = new AABB(x - 0.3, 0.0, -0.3, x + 0.3, 1.8, 0.3);
        Vector3 movement = new Vector3(0.0, 0.0, 1.0);

        MeshCollisionResult result = MeshCollider.collideWithStepUp(box, movement, mesh, 0.6f);

        assertEquals(1.0, result.allowedMovement().z, 1.0e-6, "Expected Z movement to be unmodified when sliding parallel to a wall");
    }

    @Test
    void doesNotStepUpWithoutLandingSurface() {
        float[] vertices = new float[]{
                0.35f, 0.0f, -1.0f,
                0.35f, 0.0f, 1.0f,
                0.35f, 2.0f, 1.0f,
                0.35f, 2.0f, -1.0f
        };
        int[] indices = new int[]{0, 1, 2, 0, 2, 3};
        CollisionMesh mesh = new CollisionMesh(vertices, indices);

        AABB box = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
        Vector3 movement = new Vector3(1.0, 0.0, 0.0);

        MeshCollisionResult result = MeshCollider.collideWithStepUp(box, movement, mesh, 0.6f);

        assertTrue(result.allowedMovement().y <= 1.0e-6, "Expected no step-up when there is no landing surface");
    }

    @Test
    void movesSmoothlyOverSegmentedFloor() {
        float[] vertices = new float[]{
                -1.0f, 0.0f, -1.0f,
                1.0f, 0.0f, -1.0f,
                1.0f, 0.0f, 1.0f,
                -1.0f, 0.0f, 1.0f
        };
        int[] indices = new int[]{
                0, 1, 2,
                0, 2, 3
        };
        CollisionMesh mesh = new CollisionMesh(vertices, indices);

        AABB box = new AABB(-0.5, 0.0, -0.5, 0.5, 1.8, 0.5);
        Vector3 movement = new Vector3(1.0, 0.0, 0.0);

        MeshCollisionResult result = MeshCollider.collideWithStepUp(box, movement, mesh, 0.6f);

        assertEquals(1.0, result.allowedMovement().x, 1.0e-6, "Should move freely over floor");
    }
}
