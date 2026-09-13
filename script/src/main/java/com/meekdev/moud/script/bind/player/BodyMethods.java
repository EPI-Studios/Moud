package com.meekdev.moud.script.bind.player;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.world.QueryMethods;
import com.meekdev.moud.script.bind.Values;

public final class BodyMethods {

    private BodyMethods() {}

    public static void install(LuaState state, Instance world) {
        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        methods.put("distanceTo", s -> {
            s.pushNumber(PlayerQueries.position(body(s)).distance(PlayerQueries.position(other(s, 2))));
            return 1;
        });
        methods.put("distanceSqTo", s -> {
            s.pushNumber(PlayerQueries.position(body(s)).sub(PlayerQueries.position(other(s, 2))).lengthSq());
            return 1;
        });
        methods.put("canSee", s -> {
            Character me = body(s);
            Instance them = other(s, 2);
            Vector3 from = PlayerQueries.eye(me);
            Vector3 to = PlayerQueries.eye(them);
            if (!s.isNoneOrNil(3) && from.sub(to).lengthSq() > Math.pow(s.checkNumber(3), 2)) {
                s.pushBoolean(false);
                return 1;
            }
            s.pushBoolean(QueryMethods.clear(s, world, from, to, List.of(me, them)));
            return 1;
        });
        methods.put("isGrounded", s -> {
            HumanoidState state0 = stateOf(body(s));
            s.pushBoolean(state0 == HumanoidState.STANDING || state0 == HumanoidState.RUNNING || state0 == HumanoidState.SEATED);
            return 1;
        });
        methods.put("isInWater", s -> {
            Character me = body(s);
            s.pushBoolean(me.inWater || stateOf(me) == HumanoidState.SWIMMING);
            return 1;
        });
        methods.put("isMoving", s -> {
            Character me = body(s);
            s.pushBoolean(me.velocity.lengthSq() > 0.01 || me.moveSpeed > 0.05 || stateOf(me) == HumanoidState.RUNNING);
            return 1;
        });
        methods.put("lookDirection", s -> {
            Values.push(s, look(body(s)));
            return 1;
        });
        methods.put("facing", s -> {
            Character me = body(s);
            Vector3 to = PlayerQueries.position(other(s, 2)).sub(PlayerQueries.position(me));
            to = new Vector3(to.x(), 0, to.z());
            Vector3 look = look(me);
            look = new Vector3(look.x(), 0, look.z());
            double maxAngle = s.isNoneOrNil(3) ? 45 : s.checkNumber(3);
            if (to.lengthSq() < 1e-9 || look.lengthSq() < 1e-9) {
                s.pushBoolean(true);
                return 1;
            }
            s.pushBoolean(to.normalize().dot(look.normalize()) >= Math.cos(Math.toRadians(maxAngle)));
            return 1;
        });
        Proxies.classMethods(state, Classes.CHARACTER, methods);
    }

    public static Vector3 look(Character body) {
        Quat turn = Transforms.world(body).rotation()
                .mul(Quat.axisAngle(Vector3.UP, -body.lookYaw))
                .mul(Quat.axisAngle(Vector3.RIGHT, -body.lookPitch));
        return turn.rotate(Vector3.FORWARD);
    }

    private static HumanoidState stateOf(Character body) {
        Humanoid living = Rig.humanoid(body);
        return living == null ? HumanoidState.STANDING : living.state;
    }

    public static Character body(LuaState s) {
        if (!(s.toUserDataTagged(1, Proxies.TAG) instanceof Character body)) throw s.error("expected a body");
        return body;
    }

    private static Instance other(LuaState s, int at) {
        if (!(s.toUserDataTagged(at, Proxies.TAG) instanceof Instance other)) throw s.error("expects an instance");
        return other;
    }
}
