package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;

public class Limb extends Part {

    @Prop(min = 0) public double swing;

    public double swingPhase;

    public boolean swingSideways;

    public double u;
    public double v;

    public Vector3 texels = Vector3.ZERO;

    @Prop(min = 1) public double sheetWidth = 64;
    @Prop(min = 1) public double sheetHeight = 64;

    public String sheet = "";

    public boolean mirrored;

    public boolean cutout;
}
