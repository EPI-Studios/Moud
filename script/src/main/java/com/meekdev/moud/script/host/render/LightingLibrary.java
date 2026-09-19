package com.meekdev.moud.script.host.render;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.render.Atmosphere;
import com.meekdev.moud.core.render.Daylight;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.core.render.Preset;
import com.meekdev.moud.core.render.PresetBlend;
import com.meekdev.moud.core.render.Presets;
import com.meekdev.moud.core.render.Weather;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LightingLibrary {

    private LightingLibrary() {}

    public static void install(Host host) {
        Members lighting = host.instances().of(Classes.LIGHTING);
        lighting.method("getMinutesAfterMidnight", "() -> number",
                a -> Daylight.minutesAfterMidnight(a.self(Lighting.class).clockTime));
        lighting.method("setMinutesAfterMidnight", "(minutes: number) -> ()", a -> {
            double minutes = a.number(1);
            if (!Double.isFinite(minutes)) throw new HostError("setMinutesAfterMidnight expects a number of minutes, got %s", minutes);
            host.instances().write(a.self(Lighting.class), Classes.LIGHTING.property("clockTime"), Daylight.fromMinutes(minutes));
            return null;
        });
        lighting.method("getSunDirection", "() -> Vector3", a -> {
            Lighting self = a.self(Lighting.class);
            return Daylight.sunDirection(self.clockTime, self.geographicLatitude);
        });
        lighting.method("getMoonDirection", "() -> Vector3", a -> {
            Lighting self = a.self(Lighting.class);
            return Daylight.moonDirection(self.clockTime, self.geographicLatitude);
        });

        Map<Lighting, PresetBlend> blends = new HashMap<>();
        Consumer<Double> step = dt -> {
            for (Iterator<Map.Entry<Lighting, PresetBlend>> it = blends.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Lighting, PresetBlend> running = it.next();
                if (!running.getKey().isAlive() || running.getValue().step(dt, PresetBlend.DIRECT)) it.remove();
            }
        };
        if (host.client()) host.onRenderStep(step); else host.onStep(step);

        lighting.method("getPresetNames", "() -> { string }", a -> new ArrayList<Object>(Presets.names()));
        lighting.method("applyPreset", "(name: string, seconds: number?) -> ()", a -> {
            Lighting self = a.self(Lighting.class);
            String name = a.string(1);
            double seconds = a.number(2, 0);
            Preset preset = Presets.find(name);
            if (preset == null) throw new HostError("no preset named '%s', expected one of %s", name, String.join(", ", Presets.names()));
            if (!Double.isFinite(seconds) || seconds < 0) throw new HostError("applyPreset takes 0 or more seconds, got %s", seconds);
            blends.remove(self);
            if (!host.client() || self.id() < 0) complete(self, preset, seconds);
            List<Instance> parts = Presets.parts(self);
            for (Instance part : parts) {
                for (String property : preset.of(part.def()).keySet()) {
                    PropertyDef def = part.def().property(property);
                    if (def != null) host.instances().checkWrite(part, def);
                }
            }
            PresetBlend blend = PresetBlend.of(preset, parts, seconds);
            if (!blend.step(0, PresetBlend.DIRECT)) blends.put(self, blend);
            return null;
        });

        host.instances().of(Classes.WEATHER).method("strike", "(position: Vector3?) -> ()", a -> {
            Weather self = a.self(Weather.class);
            self.strike(a.has(1) ? a.vector(1) : null);
            return null;
        });
    }

    private static void complete(Lighting lighting, Preset preset, double seconds) {
        for (ClassDef<?> def : Presets.missing(lighting)) {
            Instance made = lighting.id() < 0 ? Instances.createLocal(def, lighting, def.name()) : Instances.create(def, lighting, def.name());
            if (made instanceof Weather) continue;
            PresetBlend.apply(preset, List.of(made), PresetBlend.DIRECT);
            if (made instanceof Atmosphere && seconds > 0) Instances.setNum(made, Classes.ATMOSPHERE.property("density"), 0);
        }
    }
}
