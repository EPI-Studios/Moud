package com.meekdev.moud.script.host;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.ui.UIGradient;
import java.util.ArrayList;
import java.util.List;

final class InterfaceLibrary {

    private InterfaceLibrary() {}

    static void install(Host host) {
        host.api().alias("GradientKeypoint", "{ number | Color }");
        host.global("udim", "(scale: number, offset: number) -> UDim2", new Builtin("udim", a -> {
            double scale = a.number(0);
            double offset = a.number(1);
            return new UDim2(scale, offset, scale, offset);
        }));

        Members gradients = host.instances().of(Classes.UI_GRADIENT);
        gradients.method("setKeypoints", "(keypoints: { GradientKeypoint }) -> ()", a -> {
            UIGradient gradient = (UIGradient) a.self();
            List<UIGradient.Keypoint> stops = new ArrayList<>();
            for (Object entry : a.list(1)) {
                if (!(entry instanceof List<?> parts) || parts.size() < 2
                        || !(parts.get(0) instanceof Number time) || !(parts.get(1) instanceof Color color)) {
                    throw a.error("expects keypoints like { time, color, transparency? }");
                }
                double transparency = parts.size() > 2 && parts.get(2) instanceof Number fade ? fade.doubleValue() : 0;
                stops.add(new UIGradient.Keypoint(time.doubleValue(), color, transparency));
            }
            String written = UIGradient.write(stops);
            host.instances().checkWrite(gradient, gradient.def().property("keypoints"));
            Instances.setObj(gradient, gradient.def().property("keypoints"), written);
            return null;
        });
        gradients.method("getKeypoints", "() -> { GradientKeypoint }", a -> {
            List<Object> out = new ArrayList<>();
            for (UIGradient.Keypoint stop : ((UIGradient) a.self()).stops()) {
                out.add(new ArrayList<>(List.of(stop.time(), stop.color(), stop.transparency())));
            }
            return out;
        });
    }
}
