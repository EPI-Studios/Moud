package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class Weather extends Instance {

    public WeatherKind kind = WeatherKind.CLEAR;

    @Prop(min = 0, max = 1) public double intensity = 1;

    public Vector3 wind = new Vector3(2, 0, 1);

    @Prop(min = 0, max = 600) public double transition = 5;

    @Prop(min = 0) public int strikes;

    public Vector3 strikePosition = Vector3.ZERO;

    public final Signal<Vector3> struck = new Signal<>();

    private final List<Vector3> asked = new ArrayList<>();
    private int anywhere;

    public void strike(Vector3 at) {
        if (at == null) anywhere++;
        else asked.add(at);
    }

    public List<Vector3> takeStrikes() {
        if (asked.isEmpty()) return List.of();
        List<Vector3> taken = List.copyOf(asked);
        asked.clear();
        return taken;
    }

    public int takeStrikesAnywhere() {
        int taken = anywhere;
        anywhere = 0;
        return taken;
    }
}
