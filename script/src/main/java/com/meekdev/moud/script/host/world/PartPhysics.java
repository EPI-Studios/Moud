package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.script.api.PartPhysicsRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.host.player.Players;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;

public final class PartPhysics {

    private static final PropertyDef NETWORK_OWNER = Classes.PART.property("networkOwner");

    private PartPhysics() {}

    public static void install(Host host) {
        Members parts = host.instances().of(Classes.PART);
        parts.method("applyImpulse", "(impulse: Vector3) -> ()", a -> {
            Part part = a.self(Part.class);
            physics(host).applyImpulse(part, finite(a.vector(1)), Transforms.world(part).position());
            return null;
        });
        parts.method("applyImpulseAtPosition", "(impulse: Vector3, position: Vector3) -> ()", a -> {
            physics(host).applyImpulse(a.self(Part.class), finite(a.vector(1)), finite(a.vector(2)));
            return null;
        });
        parts.method("applyAngularImpulse", "(impulse: Vector3) -> ()", a -> {
            physics(host).applyAngularImpulse(a.self(Part.class), finite(a.vector(1)));
            return null;
        });
        parts.method("setVelocity", "(velocity: Vector3) -> ()", a -> {
            physics(host).setVelocity(a.self(Part.class), finite(a.vector(1)));
            return null;
        });
        parts.method("setAngularVelocity", "(velocity: Vector3) -> ()", a -> {
            physics(host).setAngularVelocity(a.self(Part.class), finite(a.vector(1)));
            return null;
        });
        parts.method("getVelocityAtPosition", "(position: Vector3) -> Vector3", a -> physics(host).velocityAt(a.self(Part.class), finite(a.vector(1))));
        parts.method("getMass", "() -> number", a -> {
            Part part = a.self(Part.class);
            if (host.physics() == null) return part.massless ? 0.0 : part.density * part.size.x() * part.size.y() * part.size.z();
            return host.physics().mass(part);
        });

        parts.method("setNetworkOwner", "(player: Player?) -> ()", a -> {
            Part part = a.self(Part.class);
            if (host.physics() == null) throw new HostError("network ownership is set from a server Script");
            String why = host.physics().whyNotOwnable(part);
            if (!why.isEmpty()) throw new HostError("%s can not be given an owner: %s", part.name(), why);
            String owner = "";
            if (a.has(1)) {
                owner = Players.idOf(a.get(1));
                if (owner == null) throw new HostError("setNetworkOwner expects a Player, a body with a player, or nil");
            }
            part.ownershipSet(true);
            if (!owner.equals(part.networkOwner)) part.ownerChanged();
            host.instances().write(part, NETWORK_OWNER, owner);
            return null;
        });
        parts.method("setNetworkOwnershipAuto", "() -> ()", a -> {
            if (host.physics() == null) throw new HostError("network ownership is set from a server Script");
            a.self(Part.class).ownershipSet(false);
            return null;
        });
        parts.method("isNetworkOwnershipAuto", "() -> boolean", a -> !a.self(Part.class).ownershipSet());
        parts.method("canSetNetworkOwnership", "() -> (boolean, string?)", a -> {
            Part part = a.self(Part.class);
            String why = host.physics() == null ? "network ownership is set from a server Script" : host.physics().whyNotOwnable(part);
            return why.isEmpty() ? Results.of(true) : Results.of(false, why);
        });
        parts.method("getNetworkOwner", "() -> Player?", a -> {
            String owner = a.self(Part.class).networkOwner;
            if (owner.isEmpty()) return null;
            if (host.client()) return owner.equals(host.me()) ? Players.local(host) : null;
            PlayerRef player = host.roster() == null ? null : host.roster().find(owner);
            return player == null ? null : Players.wrap(host, player);
        });

        Members spatial = host.instances().of(Classes.SPATIAL);
        spatial.method("getPivot", "() -> CFrame", a -> pivot(a.self(Spatial.class)));
        spatial.method("pivotTo", "(target: CFrame) -> ()", a -> {
            Spatial self = a.self(Spatial.class);
            CFrame target = a.cframe(1);
            CFrame move = target.mul(pivot(self).inverse());
            host.instances().write(self, Classes.SPATIAL.property("cframe"), Transforms.localFor(self, move.mul(Transforms.world(self))));
            return null;
        });

        Members models = host.instances().of(Classes.MODEL);
        models.method("getBoundingBox", "() -> (CFrame, Vector3)", a -> {
            Model model = a.self(Model.class);
            CFrame frame = pivot(model);
            double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            grow(box, model, frame.inverse());
            if (box[0] > box[3]) return Results.of(frame, Vector3.ZERO);
            Vector3 middle = new Vector3((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
            return Results.of(frame.mul(CFrame.at(middle)), new Vector3(box[3] - box[0], box[4] - box[1], box[5] - box[2]));
        });
        models.method("getScale", "() -> number", a -> a.self(Model.class).scale);
        models.method("scaleTo", "(scale: number) -> ()", a -> {
            Model model = a.self(Model.class);
            double scale = a.number(1);
            if (!(scale > 0) || !Double.isFinite(scale)) throw new HostError("scaleTo expects a number above 0, got %s", scale);
            double ratio = scale / model.scale;
            CFrame before = pivot(model);
            for (Instance child : model.children()) resize(host, child, ratio);
            host.instances().write(model, Classes.MODEL.property("scale"), scale);
            CFrame move = before.mul(pivot(model).inverse());
            host.instances().write(model, Classes.SPATIAL.property("cframe"), Transforms.localFor(model, move.mul(Transforms.world(model))));
            return null;
        });
    }

    private static PartPhysicsRef physics(Host host) {
        if (host.physics() == null) throw new HostError("parts can only be pushed from a server Script");
        return host.physics();
    }

    private static Vector3 finite(Vector3 v) {
        if (!Double.isFinite(v.x()) || !Double.isFinite(v.y()) || !Double.isFinite(v.z())) throw new HostError("expected finite numbers, got %s", v);
        return v;
    }

    static CFrame pivot(Spatial spatial) {
        if (spatial instanceof Model model && model.primaryPart instanceof Part primary && primary.isAlive() && inside(primary, model)) {
            return Transforms.world(primary);
        }
        return Transforms.world(spatial);
    }

    private static boolean inside(Instance instance, Instance ancestor) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    private static void resize(Host host, Instance instance, double ratio) {
        if (instance instanceof Spatial spatial) {
            PropertyDef frame = Classes.SPATIAL.property("cframe");
            host.instances().write(spatial, frame, spatial.cframe.withPosition(spatial.cframe.position().mul(ratio)));
            host.instances().write(spatial, Classes.SPATIAL.property("pivot"), spatial.pivot.mul(ratio));
        }
        if (instance instanceof Part part) host.instances().write(part, Classes.PART.property("size"), part.size.mul(ratio));
        for (Instance child : instance.children()) resize(host, child, ratio);
    }

    private static void grow(double[] box, Instance instance, CFrame into) {
        if (instance instanceof Part part) {
            CFrame local = into.mul(Transforms.world(part));
            Vector3 half = part.size.mul(0.5);
            for (int corner = 0; corner < 8; corner++) {
                Vector3 at = local.pointToWorld(new Vector3(
                        (corner & 1) == 0 ? -half.x() : half.x(),
                        (corner & 2) == 0 ? -half.y() : half.y(),
                        (corner & 4) == 0 ? -half.z() : half.z()));
                box[0] = Math.min(box[0], at.x());
                box[1] = Math.min(box[1], at.y());
                box[2] = Math.min(box[2], at.z());
                box[3] = Math.max(box[3], at.x());
                box[4] = Math.max(box[4], at.y());
                box[5] = Math.max(box[5], at.z());
            }
        }
        for (Instance child : instance.children()) grow(box, child, into);
    }
}
