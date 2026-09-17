package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public final class Sky extends Instance {

    @Prop(asset = true) public String skyboxBk = "";
    @Prop(asset = true) public String skyboxDn = "";
    @Prop(asset = true) public String skyboxFt = "";
    @Prop(asset = true) public String skyboxLf = "";
    @Prop(asset = true) public String skyboxRt = "";
    @Prop(asset = true) public String skyboxUp = "";

    @Prop(asset = true) public String sunTextureId = "";
    @Prop(asset = true) public String moonTextureId = "";

    @Prop(min = 0, max = 180) public double sunAngularSize = 21;
    @Prop(min = 0, max = 180) public double moonAngularSize = 11;

    @Prop(min = 0, max = 3000) public int starCount = 3000;

    public boolean celestialBodiesShown = true;

    public Vector3 skyboxOrientation = Vector3.ZERO;

    public boolean faced() {
        return !skyboxBk.isEmpty() || !skyboxDn.isEmpty() || !skyboxFt.isEmpty()
                || !skyboxLf.isEmpty() || !skyboxRt.isEmpty() || !skyboxUp.isEmpty();
    }
}
