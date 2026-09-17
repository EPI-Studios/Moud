package com.meekdev.moud.script.host.render;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.render.Daylight;
import com.meekdev.moud.core.render.Lighting;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;

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
    }
}
