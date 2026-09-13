package com.meekdev.moud.core.query;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import java.util.function.Predicate;

public final class Hits {

    public record Hit(Part part, Vector3 at, double distance) {}

    private Hits() {}

    public static Hit cast(Instance root, Vector3 from, Vector3 direction, double range) {
        return cast(root, from, direction, range, part -> true);
    }

    public static Hit cast(Instance root, Vector3 from, Vector3 direction, double range, Predicate<Part> filter) {
        Queries.Cast cast = Queries.raycast(root, from, direction, range, filter);
        return cast == null ? null : new Hit(cast.part(), cast.at(), cast.distance());
    }
}
