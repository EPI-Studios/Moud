package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Debris {

    private static final class Item {
        final Instance instance;
        double left;

        Item(Instance instance, double left) {
            this.instance = instance;
            this.left = left;
        }
    }

    private Debris() {}

    public static void install(Host host, Members game) {
        List<Item> items = new ArrayList<>();
        Members debris = new Members("Debris")
                .method("addItem", "(instance: Instance, seconds: number?) -> ()", a -> {
                    Instance instance = a.instance(1);
                    if (instance == host.world()) throw new HostError("the world cannot be thrown away");
                    double seconds = a.number(2, 10);
                    if (!Double.isFinite(seconds)) throw new HostError("debris:addItem expects a finite number of seconds");
                    items.add(new Item(instance, Math.max(0, seconds)));
                    return null;
                });
        Consumer<Double> tick = dt -> {
            if (items.isEmpty()) return;
            List<Instance> due = new ArrayList<>();
            items.removeIf(item -> {
                if (!item.instance.isAlive()) return true;
                item.left -= dt;
                if (item.left > 0) return false;
                due.add(item.instance);
                return true;
            });
            for (Instance instance : due) {
                if (instance.isAlive()) Instances.destroy(instance);
            }
        };
        if (host.client()) host.onRenderStep(tick);
        else host.onStep(tick);
        host.onClose(items::clear);
        host.declare(debris);
        game.value("debris", "Debris", debris);
    }
}
