package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.Vec3;
import java.util.function.Predicate;

public final class Hits {

    public record Hit(Part part, Vec3 at, double distance) {}

    private Hits() {}

    public static Hit cast(Instance root, Vec3 from, Vec3 direction, double range) {
        return cast(root, from, direction, range, part -> true);
    }

    public static Hit cast(Instance root, Vec3 from, Vec3 direction, double range, Predicate<Part> filter) {
        Queries.Cast cast = Queries.raycast(root, from, direction, range, filter);
        return cast == null ? null : new Hit(cast.part(), cast.at(), cast.distance());
    }
}
