package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.CameraRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;

public final class CameraMethods {

    private CameraMethods() {}

    public static void install(LuaState state, CameraRef camera) {
        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        methods.put("shake", s -> {
            camera.shake(s.checkNumber(2));
            return 0;
        });
        methods.put("kick", s -> {
            camera.kick(s.checkNumber(2), s.checkNumber(3), s.checkNumber(4), s.checkNumber(5));
            return 0;
        });
        methods.put("fovPunch", s -> {
            camera.fovPunch(s.checkNumber(2), s.checkNumber(3));
            return 0;
        });
        methods.put("clearEffects", s -> {
            camera.clearEffects();
            return 0;
        });
        methods.put("worldToScreen", s -> {
            Vector3 screen = camera.worldToScreen(Values.vec3(s, 2));
            if (screen == null) s.pushNil(); else Values.push(s, screen);
            return 1;
        });
        methods.put("screenToRay", s -> {
            CameraRef.Ray ray = camera.screenToRay(s.checkNumber(2), s.checkNumber(3));
            Values.push(s, ray.origin());
            Values.push(s, ray.direction());
            return 2;
        });
        Proxies.classMethods(state, Classes.CAMERA, methods);
    }
}
