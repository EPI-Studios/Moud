package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.*;

import java.util.*;

final class ContactCollector extends CustomContactListener {

    private final Map<Integer, Long> bodyToNode;
    private final List<CollisionEvent> pendingAdded;
    private final Set<Long> activeContacts;

    ContactCollector(Map<Integer, Long> bodyToNode, List<CollisionEvent> pendingAdded, Set<Long> activeContacts) {
        this.bodyToNode = bodyToNode;
        this.pendingAdded = pendingAdded;
        this.activeContacts = activeContacts;
    }

    @Override
    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        try {
            int idA = new Body(body1Va).getId();
            int idB = new Body(body2Va).getId();
            RVec3 offset = new ContactManifold(manifoldVa).getBaseOffset();
            Long nodeA = bodyToNode.get(idA);
            Long nodeB = bodyToNode.get(idB);
            if (nodeA != null && nodeB != null) {
                synchronized (pendingAdded) {
                    pendingAdded.add(new CollisionEvent(nodeA, nodeB,
                            (float) offset.x(), (float) offset.y(), (float) offset.z()));
                    activeContacts.add(pairKey(idA, idB));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onContactPersisted(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        try {
            int idA = new Body(body1Va).getId();
            int idB = new Body(body2Va).getId();
            if (bodyToNode.containsKey(idA) && bodyToNode.containsKey(idB)) {
                synchronized (pendingAdded) {
                    activeContacts.add(pairKey(idA, idB));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onContactRemoved(long pairVa) {}

    static long pairKey(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }
}
