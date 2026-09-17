package com.meekdev.moud.script.host.player;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.character.Tool;
import com.meekdev.moud.core.character.Tools;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.part.Seat;
import com.meekdev.moud.core.part.Seats;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        humanoids.method("applyDescription", "(description: { [string]: any }) -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "applyDescription");
            Character body = body(living);
            Map<String, Object> wanted = a.map(1, Map.of());
            for (Map.Entry<String, Object> entry : wanted.entrySet()) {
                switch (entry.getKey()) {
                    case "skin", "slim", "ears" -> write(host, body.child(Rig.APPEARANCE), entry.getKey(), entry.getValue());
                    case "hat", "head", "chest", "legs", "feet", "hatLayered" -> write(host, body.child(Rig.ARMOUR), entry.getKey(), entry.getValue());
                    case "walkSpeed", "jumpPower", "health", "maxHealth" -> write(host, living, entry.getKey(), entry.getValue());
                    case "scale", "height", "radius" -> write(host, body, entry.getKey(), entry.getValue());
                    default -> throw new HostError("a description has no '%s'", entry.getKey());
                }
            }
            return null;
        });
        humanoids.method("getAppliedDescription", "() -> { [string]: any }", a -> {
            Character body = body(a.self(Humanoid.class));
            Humanoid living = Rig.humanoid(body);
            Map<String, Object> out = new LinkedHashMap<>();
            Instance look = body.child(Rig.APPEARANCE);
            if (look instanceof Appearance appearance) {
                out.put("skin", appearance.skin);
                out.put("slim", appearance.slim);
                out.put("ears", appearance.ears);
            }
            out.put("scale", body.scale);
            out.put("height", body.height);
            out.put("radius", body.radius);
            if (living != null) {
                out.put("walkSpeed", living.walkSpeed);
                out.put("jumpPower", living.jumpPower);
                out.put("maxHealth", living.maxHealth);
            }
            return out;
        });
        humanoids.method("equipTool", "(tool: Tool) -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "equipTool");
            if (!(a.instance(1) instanceof Tool tool)) throw new HostError("equipTool expects a Tool");
            if (host.tools() == null) Tools.equip(body(living), tool);
            else host.tools().equip(body(living), tool);
            return null;
        });
        humanoids.method("unequipTools", "() -> ()", a -> {
            Humanoid living = server(host, a.self(Humanoid.class), "unequipTools");
            if (host.tools() == null) Tools.unequip(body(living));
            else host.tools().unequip(body(living));
            return null;
        });
        Members seats = host.instances().of(Classes.SEAT);
        seats.method("sit", "(humanoid: Humanoid) -> boolean", a -> {
            if (host.client()) throw new HostError("seat:sit runs in a server Script");
            if (!(a.instance(1) instanceof Humanoid living)) throw new HostError("sit expects a Humanoid");
            return Seats.sit(a.self(Seat.class), living);
        });
        Members tools = host.instances().of(Classes.TOOL);
        tools.method("activate", "() -> ()", a -> {
            Tools.activate(a.self(Tool.class), true);
            return null;
        });
        tools.method("deactivate", "() -> ()", a -> {
            Tools.activate(a.self(Tool.class), false);
            return null;
        });
        humanoids.method("getStateEnabled", "(state: " + STATE + ") -> boolean", a -> a.self(Humanoid.class).stateEnabled(state(a.string(1))));
    }

    private static void write(Host host, Instance holder, String property, Object value) {
        if (holder == null) throw new HostError("this body has no %s to change", property);
        host.instances().set(holder, property, value);
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
