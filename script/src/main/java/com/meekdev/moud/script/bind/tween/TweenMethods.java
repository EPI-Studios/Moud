package com.meekdev.moud.script.bind.tween;

import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.core.tween.Tween;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.sched.Ownership;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.bind.Values;

public final class TweenMethods {

    static final int TAG = 13;
    private static final String METHODS = "moud.tween.methods";

    record Handle(Tween tween, Signals.Handlers completed) {}

    private TweenMethods() {}

    public static void install(LuaState state, List<Tween> running, Consumer<ScriptError> onError) {
        state.newTable();
        method(state, "pause", s -> {
            handle(s).tween().pause();
            return 0;
        });
        method(state, "resume", s -> {
            handle(s).tween().resume();
            return 0;
        });
        method(state, "cancel", s -> {
            handle(s).tween().cancel();
            return 0;
        });
        method(state, "state", s -> {
            s.pushString(Enums.name(handle(s).tween().state()));
            return 1;
        });
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);

        state.newTable();
        state.pushFunction(LuaFunc.wrap(s -> {
            Handle handle = handle(s);
            String key = s.checkString(2);
            if (key.equals("completed")) {
                Signals.push(s, handle.completed());
                return 1;
            }
            s.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
            if (s.rawGetField(-1, key) != LuaType.NIL) {
                s.remove(-2);
                return 1;
            }
            throw s.error("a tween has no member '%s'", key);
        }, "tween.__index"));
        state.rawSetField(-2, "__index");
        state.setUserDataMetaTable(TAG);

        Proxies.extraMethod(state, "tween", s -> tween(s, running, onError));
    }

    private static int tween(LuaState state, List<Tween> running, Consumer<ScriptError> onError) {
        if (!(state.toUserDataTagged(1, Proxies.TAG) instanceof Instance instance)) throw state.error("expected an instance");
        if (state.type(2) != LuaType.TABLE) throw state.error("tween expects a table of goals");
        List<Tween.Goal> goals = new ArrayList<>();
        state.pushNil();
        while (state.next(2)) {
            if (state.type(-2) != LuaType.STRING) throw state.error("tween goals must be keyed by property name");
            goals.add(goal(state, instance, state.toString(-2), state.top()));
            state.pop(1);
        }
        Tween tween = new Tween(instance, goals, info(state, 3));
        Signals.Handlers completed = new Signals.Handlers();
        tween.completed.connect(done -> Signals.fire(state, completed, onError, s -> {
            s.pushBoolean(done);
            return 1;
        }));
        running.add(tween);
        Ownership owners = Ownership.of(state);
        owners.onRelease(owners.current(), tween::cancel);
        state.newUserDataTaggedWithMetatable(new Handle(tween, completed), TAG);
        return 1;
    }

    private static Tween.Goal goal(LuaState state, Instance instance, String key, int value) {
        if (instance instanceof Spatial spatial && (key.equals("position") || key.equals("rotation"))) {
            PropertyDef frame = instance.def().property("cframe");
            Proxies.checkWrite(state, instance, frame);
            CFrame world = Transforms.world(instance);
            CFrame target = key.equals("position")
                    ? Transforms.localFor(instance, world.withPosition(Values.vec3(state, value))).mul(CFrame.at(spatial.pivot))
                    : spatial.cframe.withRotation(Transforms.localFor(instance,
                            new CFrame(Vector3.ZERO, Values.cframe(state, value).rotation())).rotation());
            return new Tween.Goal(frame, target);
        }
        PropertyDef property = instance.def().property(key);
        if (property == null) throw state.error("%s has no property '%s'", instance.def().name(), key);
        Proxies.checkWrite(state, instance, property);
        return new Tween.Goal(property, Proxies.parse(state, property, value));
    }

    private static Tween.Info info(LuaState state, int at) {
        Tween.Info d = Tween.Info.DEFAULT;
        if (state.isNoneOrNil(at)) return d;
        if (state.type(at) != LuaType.TABLE) throw state.error("tween info is a table");
        double time = number(state, at, "time", d.time());
        Easing easing = option(state, at, "easing", Easing.class, d.easing());
        Easing.Direction direction = option(state, at, "direction", Easing.Direction.class, d.direction());
        int repeats = (int) number(state, at, "repeats", d.repeats());
        state.getField(at, "reverses");
        boolean reverses = state.toBoolean(-1);
        state.pop(1);
        return new Tween.Info(time, easing, direction, repeats, reverses, number(state, at, "delay", d.delay()));
    }

    private static double number(LuaState state, int at, String key, double fallback) {
        state.getField(at, key);
        double value = state.isNoneOrNil(-1) ? fallback : state.checkNumber(state.top());
        state.pop(1);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E option(LuaState state, int at, String key, Class<E> type, E fallback) {
        state.getField(at, key);
        try {
            return state.isNoneOrNil(-1) ? fallback : (E) Enums.parse(type, state.checkString(state.top()));
        } catch (IllegalArgumentException e) {
            throw state.error("%s: %s", key, e.getMessage());
        } finally {
            state.pop(1);
        }
    }

    private static Handle handle(LuaState state) {
        if (!(state.toUserDataTagged(1, TAG) instanceof Handle handle)) throw state.error("expected a tween");
        return handle;
    }

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "tween:" + name));
        state.rawSetField(-2, name);
    }
}
