package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.interp.PathCurve;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import java.util.ArrayList;
import java.util.List;

public final class CameraPath extends Spatial {

    @Prop(min = 0.01) public double duration = 5;
    public Easing easing = Easing.SINE;
    public Easing.Direction direction = Easing.Direction.IN_OUT;
    public boolean looped;
    public boolean closed;
    public boolean faceAlong;
    public Instance lookAt;

    public CFrame frameAt(PathCurve curve, double progress) {
        double alpha = easing.apply(progress, direction);
        CFrame frame = curve.sample(alpha);
        if (lookAt instanceof Spatial target && target.isAlive()) return CFrame.lookAt(frame.position(), Transforms.world(target).position());
        if (faceAlong) return new CFrame(frame.position(), Quat.lookAt(curve.tangent(alpha), Vector3.UP));
        return frame;
    }

    public List<CFrame> points() {
        List<CFrame> points = new ArrayList<>();
        for (Instance child : children()) {
            if (child instanceof Attachment point) points.add(Transforms.world(point));
        }
        return points;
    }
}
