import com.meekdev.moud.addon.java.PlaceScript;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class Main extends PlaceScript {

    private static final double TURN = Math.PI * 2;

    private final Vector3 centre = vec3(0, 64, 0);
    private final List<Instance> pivots = new ArrayList<>();
    private double clock;

    @Override
    public void run() {
        Instance world = world();
        add(world, "BloomEffect", props("intensity", 1.4, "threshold", 0.6));
        add(world, "ColorGradeEffect", props("saturation", 1.3, "contrast", 1.1));

        add(world, "Part", props("name", "floor", "size", vec3(30, 1, 30),
                "position", centre.add(vec3(0, -0.5, 0)), "color", color(0.04, 0.05, 0.08)));

        for (int level = 0; level < 5; level++) {
            Instance pivot = add(world, "Spatial", props("name", "level" + level, "position", centre.add(vec3(0, 2 + level * 1.6, 0))));
            int count = 10 + level * 2;
            double radius = 6 - level * 0.9;
            for (int i = 0; i < count; i++) {
                double angle = i / (double) count * TURN;
                add(pivot, "Part", props("name", "block",
                        "size", vec3(0.8, 0.8, 0.8),
                        "position", centre.add(vec3(Math.cos(angle) * radius, 2 + level * 1.6, Math.sin(angle) * radius)),
                        "color", hue(level / 5.0 + i / (double) count * 0.2),
                        "collides", false));
            }
            pivots.add(pivot);
        }

        Instance star = add(world, "Part", props("name", "star", "size", vec3(1.5, 1.5, 1.5),
                "position", centre.add(vec3(0, 11, 0)), "color", color(1, 0.9, 0.4), "collides", false));
        tween(star, props("size", vec3(2.4, 2.4, 2.4)),
                props("time", 0.8, "easing", "sine", "direction", "inOut", "repeats", -1, "reverses", true));
        add(world, "PointLight", props("position", centre.add(vec3(0, 11, 0)), "color", color(1, 0.85, 0.45),
                "brightness", 8, "range", 30));

        onStep(dt -> {
            clock += dt;
            for (int i = 0; i < pivots.size(); i++) {
                double direction = i % 2 == 0 ? 1 : -1;
                set(pivots.get(i), "rotation", euler(0, clock * (0.4 + i * 0.15) * direction, 0));
            }
        });

        connect(get(get(global("game"), "players"), "joined"), args -> {
            print("java sees " + get(args[0], "name") + " join");
            call(args[0], "spawn", centre.add(vec3(0, 1, 12)));
        });

        every(4, () -> print("java place has run for " + Math.round(clock) + " seconds"));
        print("java place running with " + pivots.size() + " rings");
    }

    private static Color hue(double t) {
        double a = t * TURN;
        return color(0.5 + 0.5 * Math.cos(a), 0.5 + 0.5 * Math.cos(a - 2.094), 0.5 + 0.5 * Math.cos(a + 2.094));
    }
}
