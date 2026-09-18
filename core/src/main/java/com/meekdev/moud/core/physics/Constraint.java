package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public class Constraint extends Instance {

    public Instance attachment0;
    public Instance attachment1;
    public boolean enabled = true;
    public boolean visible;
    public boolean collideConnected;
    public Color color = new Color(0.64f, 0.64f, 0.64f);

    @Prop(driven = true) public boolean active;
}
