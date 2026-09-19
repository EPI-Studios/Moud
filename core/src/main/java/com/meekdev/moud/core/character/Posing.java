package com.meekdev.moud.core.character;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import java.util.IdentityHashMap;
import java.util.Map;

public final class Posing {

    private static final class Touch {
        final CFrame input;
        CFrame written;

        Touch(CFrame input) {
            this.input = input;
        }
    }

    private static final Map<Instance, Touch> TOUCHED = new IdentityHashMap<>();

    private Posing() {}

    public static void step(InstanceTree tree, double dt) {
        restore();
        IKControls.solve(tree, dt);
        JointSprings.step(tree, dt);
        remember();
    }

    public static void forget() {
        TOUCHED.clear();
        IKControls.forget();
        JointSprings.forget();
    }

    static void touch(Instance joint) {
        TOUCHED.computeIfAbsent(joint, key -> new Touch(Rigs.transform(key)));
    }

    private static void restore() {
        for (Map.Entry<Instance, Touch> entry : TOUCHED.entrySet()) {
            Instance joint = entry.getKey();
            Touch touch = entry.getValue();
            if (joint.isAlive() && touch.written != null && Rigs.transform(joint).equals(touch.written)) {
                Rigs.transform(joint, touch.input);
            }
        }
        TOUCHED.clear();
    }

    private static void remember() {
        for (Map.Entry<Instance, Touch> entry : TOUCHED.entrySet()) {
            if (entry.getKey().isAlive()) entry.getValue().written = Rigs.transform(entry.getKey());
        }
    }
}
