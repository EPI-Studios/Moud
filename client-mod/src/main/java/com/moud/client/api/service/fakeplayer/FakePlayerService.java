package com.moud.client.api.service.fakeplayer;

import com.moud.network.MoudPackets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class FakePlayerService {
    private ExecutorService executor;
    private Context context;
    private Sender sender;

    public FakePlayerService() {
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    @HostAccess.Export
    public void spawn(Object descriptorObj) {
        if (sender == null || descriptorObj == null) return;
        MoudPackets.FakePlayerDescriptor descriptor = FakePlayerShim.fromValue(descriptorObj);
        sender.send(new MoudPackets.C2S_SpawnFakePlayer(descriptor));
    }

    @HostAccess.Export
    public void remove(long id) {
        if (sender == null) return;
        sender.send(new MoudPackets.C2S_RemoveFakePlayer(id));
    }

    @HostAccess.Export
    public void setPose(long id, boolean sneaking, boolean sprinting, boolean swinging, boolean usingItem) {
        if (sender == null) return;
        sender.send(new MoudPackets.C2S_SetFakePlayerPose(id, sneaking, sprinting, swinging, usingItem));
    }

    @HostAccess.Export
    public void setPath(long id, Object pathObj) {
        if (sender == null || pathObj == null) return;
        var v = (org.graalvm.polyglot.Value) pathObj;
        var waypoints = new java.util.ArrayList<MoudPackets.FakePlayerWaypoint>();
        if (v.hasMember("waypoints") && v.getMember("waypoints").hasArrayElements()) {
            var arr = v.getMember("waypoints");
            long len = arr.getArraySize();
            for (long i = 0; i < len; i++) {
                var wp = arr.getArrayElement(i);
                if (wp != null && wp.hasMember("position")) {
                    var p = wp.getMember("position");
                    if (p != null && p.hasMember("x")) {
                        waypoints.add(new MoudPackets.FakePlayerWaypoint(new com.moud.api.math.Vector3(
                                p.getMember("x").asDouble(),
                                p.getMember("y").asDouble(),
                                p.getMember("z").asDouble()
                        )));
                    }
                }
            }
        }
        double speed = v.hasMember("speed") ? v.getMember("speed").asDouble() : 0.0;
        boolean loop = v.hasMember("loop") && v.getMember("loop").asBoolean();
        boolean ping = v.hasMember("pingPong") && v.getMember("pingPong").asBoolean();
        sender.send(new MoudPackets.C2S_SetFakePlayerPath(id, waypoints, speed, loop, ping));
    }

    public interface Sender {
        void send(Object packet);
    }

    private static final class FakePlayerShim {
        static MoudPackets.FakePlayerDescriptor fromValue(Object raw) {
            if (!(raw instanceof org.graalvm.polyglot.Value v)) {
                throw new IllegalArgumentException("Expected a descriptor object");
            }
            long id = v.hasMember("id") ? v.getMember("id").asLong() : 0L;
            String label = v.hasMember("label") ? v.getMember("label").asString() : "Fake Player";
            String skin = v.hasMember("skinUrl") ? v.getMember("skinUrl").asString() : "";
            var pos = v.getMember("position");
            com.moud.api.math.Vector3 position = pos != null && pos.hasMember("x") ?
                    new com.moud.api.math.Vector3(pos.getMember("x").asDouble(), pos.getMember("y").asDouble(), pos.getMember("z").asDouble()) :
                    new com.moud.api.math.Vector3(0, 64, 0);
            com.moud.api.math.Quaternion rot = v.hasMember("rotation") ? toQuat(v.getMember("rotation")) : com.moud.api.math.Quaternion.identity();
            double width = v.hasMember("width") ? v.getMember("width").asDouble() : 0.6;
            double height = v.hasMember("height") ? v.getMember("height").asDouble() : 1.8;
            boolean physics = v.hasMember("physicsEnabled") && v.getMember("physicsEnabled").asBoolean();
            boolean sneaking = v.hasMember("sneaking") && v.getMember("sneaking").asBoolean();
            boolean sprinting = v.hasMember("sprinting") && v.getMember("sprinting").asBoolean();
            boolean swinging = v.hasMember("swinging") && v.getMember("swinging").asBoolean();
            boolean usingItem = v.hasMember("usingItem") && v.getMember("usingItem").asBoolean();
            List<MoudPackets.FakePlayerWaypoint> path = new ArrayList<>();
            if (v.hasMember("path") && v.getMember("path").hasArrayElements()) {
                var arr = v.getMember("path");
                long len = arr.getArraySize();
                for (long i = 0; i < len; i++) {
                    var wp = arr.getArrayElement(i);
                    if (wp != null && wp.hasMember("position")) {
                        var p = wp.getMember("position");
                        if (p != null && p.hasMember("x")) {
                            path.add(new MoudPackets.FakePlayerWaypoint(new com.moud.api.math.Vector3(
                                    p.getMember("x").asDouble(),
                                    p.getMember("y").asDouble(),
                                    p.getMember("z").asDouble())));
                        }
                    }
                }
            }
            double pathSpeed = v.hasMember("pathSpeed") ? v.getMember("pathSpeed").asDouble() : 0.0;
            boolean pathLoop = v.hasMember("pathLoop") && v.getMember("pathLoop").asBoolean();
            boolean pathPingPong = v.hasMember("pathPingPong") && v.getMember("pathPingPong").asBoolean();
            return new MoudPackets.FakePlayerDescriptor(id, label, skin, position, rot, width, height, physics,
                    sneaking, sprinting, swinging, usingItem, path, pathSpeed, pathLoop, pathPingPong);
        }

        private static com.moud.api.math.Quaternion toQuat(org.graalvm.polyglot.Value v) {
            if (v.hasMember("x")) {
                double pitch = v.getMember("x").asDouble();
                double yaw = v.getMember("y").asDouble();
                double roll = v.getMember("z").asDouble();
                return com.moud.api.math.Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
            }
            return com.moud.api.math.Quaternion.identity();
        }
    }
}
