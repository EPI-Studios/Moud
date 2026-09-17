package com.meekdev.moud.script.host.player;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.DevicesRef;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UserInput {

    private static final Members INPUT_OBJECT = new Members("InputObject")
            .declare("inputType", "string")
            .declare("keyCode", "string")
            .declare("state", "InputState")
            .declare("position", "Vector3")
            .declare("delta", "Vector3");

    private static final Members GAMEPAD_STATE = new Members("GamepadState")
            .declare("gamepad", "number")
            .declare("leftStick", "Vector3")
            .declare("rightStick", "Vector3")
            .declare("leftTrigger", "number")
            .declare("rightTrigger", "number")
            .declare("buttons", "{ [string]: boolean }");

    private static final Comparator<Binding> FIRST = Comparator.comparingInt(Binding::priority).reversed()
            .thenComparing(Comparator.comparingLong(Binding::order).reversed());

    static final class InputObject implements HostObject {

        private final String type;
        private final String key;
        private String state;
        private Vector3 position;
        private Vector3 delta;

        InputObject(String type, String key, String state, Vector3 position, Vector3 delta) {
            this.type = type;
            this.key = key;
            this.state = state;
            this.position = position;
            this.delta = delta;
        }

        @Override
        public String typeName() {
            return "InputObject";
        }

        @Override
        public Object get(String name) {
            return switch (name) {
                case "inputType" -> type;
                case "keyCode" -> key;
                case "state" -> state;
                case "position" -> position;
                case "delta" -> delta;
                default -> throw new HostError("InputObject has no member '%s'", name);
            };
        }
    }

    private record Pad(DevicesRef.Gamepad pad) implements HostObject {

        @Override
        public String typeName() {
            return "GamepadState";
        }

        @Override
        public Object get(String name) {
            return switch (name) {
                case "gamepad" -> (double) pad.index() + 1;
                case "leftStick" -> pad.leftStick();
                case "rightStick" -> pad.rightStick();
                case "leftTrigger" -> pad.leftTrigger();
                case "rightTrigger" -> pad.rightTrigger();
                case "buttons" -> new LinkedHashMap<String, Object>(pad.buttons());
                default -> throw new HostError("GamepadState has no member '%s'", name);
            };
        }
    }

    private record Binding(String name, Callable handler, int priority, List<String> keys, long order, Object owner) {

        boolean hears(DevicesRef.Event event) {
            for (String key : keys) {
                if (key.equals(event.key()) || key.equalsIgnoreCase(event.type())) return true;
            }
            return false;
        }
    }

    private final Host host;
    private final DevicesRef devices;
    private final HostSignal began;
    private final HostSignal changed;
    private final HostSignal ended;
    private final HostSignal connected;
    private final HostSignal disconnected;
    private final Map<String, InputObject> live = new HashMap<>();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private long bound;

    private UserInput(Host host, DevicesRef devices) {
        this.host = host;
        this.devices = devices;
        began = new HostSignal(host, "InputSignal", "input.inputBegan");
        changed = new HostSignal(host, "InputSignal", "input.inputChanged");
        ended = new HostSignal(host, "InputSignal", "input.inputEnded");
        connected = new HostSignal(host, "GamepadSignal", "input.gamepadConnected");
        disconnected = new HostSignal(host, "GamepadSignal", "input.gamepadDisconnected");
        host.onClose(() -> {
            for (Binding binding : bindings.values()) binding.handler().release();
            bindings.clear();
        });
    }

    static UserInput install(Host host, Members input) {
        DevicesRef devices = host.devices();
        if (devices == null) return null;
        host.api().alias("InputState", "\"begin\" | \"change\" | \"end\" | \"cancel\"");
        host.api().alias("MouseBehavior", "\"default\" | \"lockCenter\" | \"lockCurrentPosition\"");
        host.api().alias("ActionHandler", "(name: string, state: InputState, input: InputObject) -> (\"sink\" | \"pass\")?");
        host.api().declare(HostSignal.decl("InputSignal", "(input: InputObject, gameProcessed: boolean) -> ()"));
        host.api().declare(HostSignal.decl("GamepadSignal", "(gamepad: number) -> ()"));
        host.api().declare(INPUT_OBJECT.decl());
        host.api().declare(GAMEPAD_STATE.decl());
        UserInput events = new UserInput(host, devices);
        input.value("inputBegan", "InputSignal", events.began)
                .value("inputChanged", "InputSignal", events.changed)
                .value("inputEnded", "InputSignal", events.ended)
                .value("gamepadConnected", "GamepadSignal", events.connected)
                .value("gamepadDisconnected", "GamepadSignal", events.disconnected)
                .field("mouseBehavior", "MouseBehavior", devices::mouseBehavior, value -> {
                    if (!(value instanceof String behavior) || !DevicesRef.MOUSE_BEHAVIORS.contains(behavior)) {
                        throw new HostError("input.mouseBehavior expects %s", String.join(", ", DevicesRef.MOUSE_BEHAVIORS));
                    }
                    devices.mouseBehavior(behavior);
                })
                .field("mouseIconEnabled", "boolean", devices::mouseIconEnabled, value -> {
                    if (!(value instanceof Boolean on)) throw new HostError("input.mouseIconEnabled expects true or false");
                    devices.mouseIconEnabled(on);
                })
                .method("isKeyDown", "(key: string) -> boolean", a -> devices.keyDown(a.string(1)))
                .method("isMouseButtonPressed", "(button: string | number) -> boolean", a -> devices.mouseButtonDown(button(a.get(1))))
                .method("getKeysPressed", "() -> { InputObject }", a -> {
                    List<Object> out = new ArrayList<>();
                    for (String key : devices.keysDown()) out.add(new InputObject("keyboard", key, "begin", Vector3.ZERO, Vector3.ZERO));
                    return out;
                })
                .method("keyName", "(key: string) -> string", a -> devices.keyName(a.string(1)))
                .method("getConnectedGamepads", "() -> { number }", a -> {
                    List<Object> out = new ArrayList<>();
                    for (DevicesRef.Gamepad pad : devices.gamepads()) out.add((double) pad.index() + 1);
                    return out;
                })
                .method("getGamepadState", "(gamepad: number?) -> GamepadState?", a -> {
                    int index = a.has(1) ? a.integer(1) - 1 : -1;
                    for (DevicesRef.Gamepad pad : devices.gamepads()) {
                        if (index < 0 || pad.index() == index) return new Pad(pad);
                    }
                    return null;
                })
                .method("bindAction", "(name: string, handler: ActionHandler, ...string) -> ()", a -> {
                    events.bind(a.string(1), a.callable(2), 0, keys(devices, a.from(3)));
                    return null;
                })
                .method("bindActionAtPriority", "(name: string, handler: ActionHandler, priority: number, ...string) -> ()", a -> {
                    events.bind(a.string(1), a.callable(2), a.integer(3), keys(devices, a.from(4)));
                    return null;
                })
                .method("unbindAction", "(name: string) -> ()", a -> {
                    events.unbind(a.string(1));
                    return null;
                })
                .method("getBoundActions", "() -> { string }", a -> new ArrayList<Object>(events.bindings.keySet()));
        return events;
    }

    private static int button(Object value) {
        return switch (value) {
            case Number n when n.intValue() >= 1 && n.intValue() <= 3 -> n.intValue();
            case String s when s.equalsIgnoreCase("mouseButton1") -> 1;
            case String s when s.equalsIgnoreCase("mouseButton2") -> 2;
            case String s when s.equalsIgnoreCase("mouseButton3") -> 3;
            case null, default -> throw new HostError("isMouseButtonPressed expects 1, 2, 3 or \"mouseButton1\", \"mouseButton2\", \"mouseButton3\"");
        };
    }

    private static List<String> keys(DevicesRef devices, Object[] given) {
        List<String> keys = new ArrayList<>(given.length);
        for (Object key : given) {
            if (!(key instanceof String name)) throw new HostError("an action is bound to key names, got %s", Host.typeOf(key));
            keys.add(devices.keyCode(name));
        }
        if (keys.isEmpty()) throw new HostError("an action needs at least one key to listen to");
        return keys;
    }

    private void bind(String name, Callable handler, int priority, List<String> keys) {
        unbind(name);
        Object owner = host.ownership().current();
        Binding binding = new Binding(name, handler.retain(), priority, List.copyOf(keys), bound++, owner);
        bindings.put(name, binding);
        host.ownership().onRelease(owner, () -> {
            if (bindings.get(name) == binding) unbind(name);
        });
    }

    private void unbind(String name) {
        Binding old = bindings.remove(name);
        if (old != null) old.handler().release();
    }

    public boolean dispatch(DevicesRef.Event event) {
        String id = event.type() + ":" + event.key();
        InputObject object = live.get(id);
        if (object == null || event.state().equals("begin")) {
            object = new InputObject(event.type(), event.key(), event.state(), event.position(), event.delta());
            live.put(id, object);
        } else {
            object.state = event.state();
            object.position = event.position();
            object.delta = event.delta();
        }
        if (event.state().equals("end") || event.state().equals("cancel")) live.remove(id);
        boolean sunk = !event.processed() && act(event, object);
        HostSignal signal = switch (event.state()) {
            case "begin" -> began;
            case "change" -> changed;
            default -> ended;
        };
        signal.fire(object, event.processed() || sunk);
        return sunk;
    }

    private boolean act(DevicesRef.Event event, InputObject object) {
        if (bindings.isEmpty()) return false;
        List<Binding> hearing = new ArrayList<>();
        for (Binding binding : bindings.values()) if (binding.hears(event)) hearing.add(binding);
        hearing.sort(FIRST);
        for (Binding binding : hearing) {
            if (bindings.get(binding.name()) != binding) continue;
            Object before = host.ownership().enter(binding.owner());
            Object[] out;
            try {
                out = host.call(binding.handler(), "input." + binding.name(), binding.name(), event.state(), object);
            } finally {
                host.ownership().leave(before);
            }
            if (out == null || out.length == 0 || !"pass".equals(out[0])) return true;
        }
        return false;
    }

    public void gamepad(int index, boolean on) {
        (on ? connected : disconnected).fire((double) index + 1);
    }
}
