package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.physics.BodyHandle;
import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.CollisionEvent;
import com.moud.server.minestom.physics.JoltPhysicsWorld;
import com.moud.server.minestom.scripting.lang.RuntimeScriptUtil;
import com.moud.server.minestom.scripting.physics.PhysicsHit;
import com.moud.server.minestom.scripting.runtime.RuntimeFacade;
import org.graalvm.polyglot.HostAccess;

import java.util.List;

@LuauExport(name = "PhysicsApi", doc = "Raycasts, overlap queries, character body movement, and force application against the physics world.")
public final class PhysicsApi {
    private final ServerScene scene;
    private final RuntimeFacade runtime;
    private final NodeApi nodeApi;

    public PhysicsApi(ServerScene scene, RuntimeFacade runtime, NodeApi nodeApi) {
        this.scene = scene;
        this.runtime = runtime;
        this.nodeApi = nodeApi;
    }

    @HostAccess.Export
    @LuauExport
    public PhysicsHit raycast(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return null;
        }
        return physics.raycast(ox, oy, oz, dx, dy, dz, maxDist)
                .map(r -> new PhysicsHit(r, physics))
                .orElse(null);
    }

    @HostAccess.Export
    @LuauExport
    public int[] overlapSphere(double x, double y, double z, double radius) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new int[0];
        }
        List<BodyHandle> handles = physics.overlapSphere(x, y, z, radius);
        int[] ids = new int[handles.size()];
        for (int i = 0; i < handles.size(); i++) {
            ids[i] = handles.get(i).id();
        }
        return ids;
    }

    @HostAccess.Export
    @LuauExport
    public CollisionEvent[] getCollisionEvents() {
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new CollisionEvent[0];
        }
        List<CollisionEvent> events = physics.consumeCollisionEvents();
        return events.toArray(new CollisionEvent[0]);
    }

    @HostAccess.Export
    @LuauExport
    public double[] getBodyVelocity(long nodeId) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        float[] v = physics.getLinearVelocity(nodeId);
        return new double[]{v[0], v[1], v[2]};
    }

    @HostAccess.Export
    @LuauExport
    public double[] getCharacterVelocity(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || !"CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{
                nodeApi.getNumber(nodeId, "velocity_x", 0.0),
                nodeApi.getNumber(nodeId, "velocity_y", 0.0),
                nodeApi.getNumber(nodeId, "velocity_z", 0.0)
        };
    }

    @HostAccess.Export
    @LuauExport
    public void setCharacterVelocity(long nodeId, double vx, double vy, double vz) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || !"CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
            return;
        }
        runtime.mutator().queueSet(nodeId, "script_controlled", "true");
        runtime.mutator().queueSet(nodeId, "velocity_x", RuntimeScriptUtil.trimFloat((float) vx));
        runtime.mutator().queueSet(nodeId, "velocity_y", RuntimeScriptUtil.trimFloat((float) vy));
        runtime.mutator().queueSet(nodeId, "velocity_z", RuntimeScriptUtil.trimFloat((float) vz));
    }

    @HostAccess.Export
    @LuauExport
    public void setCharacterScriptControlled(long nodeId, boolean controlled) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null || !"CharacterBody3D".equals(scene.engine().nodeTypes().typeIdFor(node))) {
            return;
        }
        runtime.mutator().queueSet(nodeId, "script_controlled", Boolean.toString(controlled));
        if (!controlled) {
            runtime.mutator().queueSet(nodeId, "velocity_x", "0");
            runtime.mutator().queueSet(nodeId, "velocity_y", "0");
            runtime.mutator().queueSet(nodeId, "velocity_z", "0");
        }
    }

    @HostAccess.Export
    @LuauExport
    public double[] moveAndSlide(long nodeId, double deltaSeconds) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) {
            return new double[]{0.0, 0.0, 0.0};
        }
        runtime.mutator().queueSet(nodeId, "script_controlled", "true");
        double vx = nodeApi.getNumber(nodeId, "velocity_x", 0.0);
        double vy = nodeApi.getNumber(nodeId, "velocity_y", 0.0);
        double vz = nodeApi.getNumber(nodeId, "velocity_z", 0.0);
        return runtime.characterBodySimulator().moveAndSlide(scene, node, deltaSeconds, vx, vy, vz);
    }

    @HostAccess.Export
    @LuauExport
    public boolean isOnFloor(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return node != null && runtime.characterBodySimulator().isOnFloor(node);
    }

    @HostAccess.Export
    @LuauExport
    public boolean isOnWall(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return node != null && runtime.characterBodySimulator().isOnWall(node);
    }

    @HostAccess.Export
    @LuauExport
    public boolean isOnCeiling(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return node != null && runtime.characterBodySimulator().isOnCeiling(node);
    }

    @HostAccess.Export
    @LuauExport
    public double[] getWallNormal(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return runtime.characterBodySimulator().wallNormal(node);
    }

    @HostAccess.Export
    @LuauExport
    public double[] getInputDirection(long nodeId) {
        Node node = scene.engine().sceneTree().getNode(nodeId);
        return runtime.characterBodySimulator().inputDirection(node);
    }

    @HostAccess.Export
    @LuauExport
    public void applyForce(long nodeId, double fx, double fy, double fz) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics != null) {
            physics.applyForce(nodeId, (float) fx, (float) fy, (float) fz);
        }
    }

    @HostAccess.Export
    @LuauExport
    public void applyImpulse(long nodeId, double fx, double fy, double fz) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics != null) {
            physics.applyImpulse(nodeId, (float) fx, (float) fy, (float) fz);
        }
    }

    @HostAccess.Export
    @LuauExport
    public void setLinearVelocity(long nodeId, double vx, double vy, double vz) {
        JoltPhysicsWorld physics = scene.physics();
        if (physics != null) {
            physics.setLinearVelocity(nodeId, (float) vx, (float) vy, (float) vz);
        }
    }
}
