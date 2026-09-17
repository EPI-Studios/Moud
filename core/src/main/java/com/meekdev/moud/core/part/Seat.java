package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class Seat extends Part {

    public boolean disabled;

    public Instance occupant;

    public Seat() {
        size = new Vector3(2, 0.4, 2);
        color = new Color(0.25f, 0.35f, 0.7f, 1f);
    }
}
