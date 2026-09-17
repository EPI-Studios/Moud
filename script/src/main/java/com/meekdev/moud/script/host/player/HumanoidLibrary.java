package com.meekdev.moud.script.host.player;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.List;

final class HumanoidLibrary {

    private static final String STATE = "\"" + String.join("\" | \"", Enums.names(HumanoidState.class)) + "\"";

    private HumanoidLibrary() {}

    static void install(Host host) {
        host.api().declare(HostSignal.decl("NumberSignal", "(value: number) -> ()"));
        host.api().declare(HostSignal.decl("BoolSignal", "(active: boolean) -> ()"));
        Members humanoids = host.instances().of(Classes.HUMANOID);
        humanoids.method("move", "(direction: Vector3, relativeToCamera: boolean?) -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "move");
            Walkers.move(body(living), a.vector(1), a.has(2) && Boolean.TRUE.equals(a.get(2)));
            return null;
        });
        humanoids.method("moveTo", "(point: Vector3) -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "moveTo");
            Character body = body(living);
            Vector3 point = a.vector(1);
            if (body.hasPlayer()) {
                Walkers.walk(body, List.of(point));
            } else {
                Humanoids.move(body, Vector3.ZERO);
                Instances.setObj(living, Classes.HUMANOID.property("walkTo"), point);
                Instances.setBool(living, Classes.HUMANOID.property("walking"), true);
            }
            return null;
        });
        humanoids.method("takeDamage", "(amount: number) -> ()", a -> {
            Humanoids.takeDamage(server(host, a.self(Humanoid.class), "takeDamage"), a.number(1));
            return null;
        });
        humanoids.method("changeState", "(state: " + STATE + ") -> ()", a -> {
            Humanoids.changeState(body(server(host, a.self(Humanoid.class), "changeState")), state(a.string(1)));
            return null;
        });
        humanoids.method("getState", "() -> " + STATE, a -> Enums.name(a.self(Humanoid.class).state));
        humanoids.method("setStateEnabled", "(state: " + STATE + ", enabled: boolean) -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "setStateEnabled");
            if (!(a.get(2) instanceof Boolean on)) throw new HostError("setStateEnabled expects true or false");
            living.stateEnabled(state(a.string(1)), on);
            return null;
        });
        humanoids.method("getStateEnabled", "(state: " + STATE + ") -> boolean", a -> a.self(Humanoid.class).stateEnabled(state(a.string(1))));
    }

    private static Humanoid server(Host host, Humanoid living, String what) {
        if (host.client()) throw new HostError("humanoid:%s runs in a server Script", what);
        return living;
    }

    private static Character body(Humanoid living) {
        if (!(living.parent() instanceof Character body)) throw new HostError("this humanoid is not inside a body");
        return body;
    }

    private static HumanoidState state(String name) {
        try {
            return (HumanoidState) Enums.parse(HumanoidState.class, name);
        } catch (IllegalArgumentException e) {
            throw new HostError("'%s' is not a humanoid state, expected %s", name, String.join(", ", Enums.names(HumanoidState.class)));
        }
    }
}
