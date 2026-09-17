package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.render.Camera;
import java.util.ArrayList;
import java.util.List;

public final class ViewportFrame extends GuiObject {

    public static final double DEFAULT_FOV = 70;

    public Instance camera;

    public Color ambient = new Color(0.45f, 0.45f, 0.5f, 1f);
    public Color lightColor = new Color(0.8f, 0.8f, 0.78f, 1f);
    public Vector3 lightDirection = new Vector3(-1, -1, -1);

    public Color imageColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double imageTransparency;

    @Prop(min = 0) public double updateRate;
    @Prop(min = 0.1, max = 4) public double resolutionScale = 1;

    private double aspect = 1;

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
        return around(instance) != null || Instance.outOfWorld(instance);
    }

    public void measured(double width, double height) {
        if (width > 0 && height > 0) aspect = width / height;
    }

    public double aspect() {
        return aspect;
    }

    public Camera viewCamera() {
        if (camera instanceof Camera chosen && chosen.isAlive() && around(chosen) == this) return chosen;
        for (Instance child : children()) {
            if (child instanceof Camera found) return found;
        }
        return null;
    }

    public double fov() {
        Camera chosen = viewCamera();
        return chosen != null && chosen.fov > 0 ? chosen.fov : DEFAULT_FOV;
    }

    public CFrame view() {
        Camera chosen = viewCamera();
        if (chosen != null) return Transforms.world(chosen);
        List<Part> shown = parts();
        if (shown.isEmpty()) return CFrame.lookAt(new Vector3(0, 0, 5), Vector3.ZERO);
        Vector3 sum = Vector3.ZERO;
        for (Part part : shown) sum = sum.add(Transforms.world(part).position());
        Vector3 centre = sum.mul(1.0 / shown.size());
        double radius = 0.1;
        for (Part part : shown) radius = Math.max(radius, Transforms.world(part).position().distance(centre) + part.size.length() * 0.5);
        double distance = radius / Math.sin(Math.toRadians(DEFAULT_FOV) * 0.5);
        return CFrame.lookAt(centre.add(new Vector3(1, 0.7, 1).normalize().mul(distance)), centre);
    }

    public Vector3[] ray(double x, double y) {
        CFrame frame = view();
        double tall = Math.tan(Math.toRadians(fov()) * 0.5);
        Vector3 local = new Vector3((x * 2 - 1) * tall * aspect, (1 - y * 2) * tall, -1).normalize();
        return new Vector3[] {frame.position(), frame.vectorToWorld(local)};
    }

    public List<Part> parts() {
        List<Part> found = new ArrayList<>();
        collect(this, found);
        return found;
    }

    private static void collect(Instance at, List<Part> found) {
        for (Instance child : at.children()) {
            if (child instanceof ViewportFrame) continue;
            if (child instanceof Part part) found.add(part);
            collect(child, found);
        }
    }
}
