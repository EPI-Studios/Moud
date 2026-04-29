package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;

public final class ClientPhysicsApi {

    public void applyImpulse(long nodeId, double jx, double jy, double jz) {
        ClientRapierPhysics.get().applyPredictedImpulse(nodeId, jx, jy, jz);
    }

    public ClientRayHit raycast(double ox, double oy, double oz,
                                double dx, double dy, double dz,
                                double maxDist) {
        return ClientRapierPhysics.get()
                .raycastAny(ox, oy, oz, dx, dy, dz, maxDist)
                .map(h -> new ClientRayHit(h.x(), h.y(), h.z(),
                        h.nx(), h.ny(), h.nz(), h.distance()))
                .orElse(null);
    }
}
