package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.script.api.PartPhysicsRef;
import com.meekdev.moud.script.host.HostError;
import java.util.function.Consumer;

public final class PartBodies implements PartPhysicsRef {

    public static final PartBodies INSTANCE = new PartBodies();

    private static final PropertyDef OWNER = Classes.PART.property("networkOwner");
    private static final int SERVER_HOLD_TICKS = 40;

    private PartBodies() {}

    @Override
    public void applyImpulse(Part part, Vector3 impulse, Vector3 at) {
        push(part, body -> body.applyImpulseAt(BoxFrames.vec(impulse), BoxFrames.vec(at)));
    }

    @Override
    public void applyAngularImpulse(Part part, Vector3 impulse) {
        push(part, body -> body.applyAngularImpulse(BoxFrames.vec(impulse)));
    }

    @Override
    public void setVelocity(Part part, Vector3 velocity) {
        push(part, body -> body.setLinearVelocity(BoxFrames.vec(velocity)));
    }

    @Override
    public void setAngularVelocity(Part part, Vector3 velocity) {
        push(part, body -> body.setAngularVelocity(BoxFrames.vec(velocity)));
    }

    @Override
    public String whyNotOwnable(Part part) {
        if (part.anchored) return "an anchored part is always the server's";
        if (!part.collides) return "a part that does not collide is not simulated";
        if (Joints.holds(part.id())) return "a part held by a constraint stays with the server";
        if (Instance.outOfWorld(part)) return "a part kept in storage is not simulated";
        for (Instance up = part.parent(); up != null; up = up.parent()) {
            if (up instanceof Character) return "a part of a body moves with the body";
        }
        return "";
    }

    @Override
    public Vector3 velocityAt(Part part, Vector3 position) {
        B3Body body = Physics.shapes().body(part.id());
        if (part.anchored || body == null || !body.isValid()) return part.velocity;
        return BoxFrames.vector(body.velocityAtPoint(BoxFrames.vec(position)));
    }

    @Override
    public double mass(Part part) {
        B3Body body = Physics.shapes().body(part.id());
        if (body != null && body.isValid() && body.type() == B3BodyType.DYNAMIC) return body.mass();
        return part.massless ? 0 : part.density * part.size.x() * part.size.y() * part.size.z();
    }

    private static void push(Part part, Consumer<B3Body> action) {
        if (part.anchored) throw new HostError("%s is anchored, physics does not move it", part.name());
        if (!part.collides) throw new HostError("%s does not collide, so it has no physics body", part.name());
        if (!part.networkOwner.isEmpty()) {
            if (part.ownershipSet()) {
                throw new HostError("%s is simulated by its network owner, give it back with setNetworkOwner(nil) or setNetworkOwnershipAuto() first", part.name());
            }
            part.ownerChanged();
            Instances.setObj(part, OWNER, "");
            Physics.shapes().retype(part);
        }
        if (!part.ownershipSet()) part.holdForServer(SERVER_HOLD_TICKS);
        Physics.shapes().withBody(part, body -> {
            if (!body.isValid()) return;
            action.accept(body);
            body.setAwake(true);
        });
    }
}
