package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.tween.Easing;

public final class KeyframePose extends Instance {

    public CFrame cframe = CFrame.IDENTITY;

    public Easing easing = Easing.LINEAR;

    public Easing.Direction direction = Easing.Direction.IN_OUT;

    @Prop(min = 0, max = 1) public double weight = 1;
}
