package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class WeldConstraint extends Instance {

    public Instance part0;
    public Instance part1;
    public boolean enabled = true;

    @Prop(driven = true, readOnly = true) public boolean active;
}
