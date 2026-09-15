package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public final class ViewportFrame extends GuiObject {

    public Instance camera;

    public Color ambient = new Color(0.45f, 0.45f, 0.5f, 1f);
    public Color lightColor = new Color(0.8f, 0.8f, 0.78f, 1f);
    public Vector3 lightDirection = new Vector3(-1, -1, -1);

    public Color imageColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double imageTransparency;

    @Prop(min = 0) public double updateRate;
    @Prop(min = 0.1, max = 4) public double resolutionScale = 1;

    public ViewportFrame() {
        backgroundTransparency = 1;
    }

    public static ViewportFrame around(Instance instance) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at instanceof ViewportFrame viewport) return viewport;
        }
        return null;
    }

    public static boolean inside(Instance instance) {
        return around(instance) != null;
    }
}
