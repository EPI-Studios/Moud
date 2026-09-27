package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes.World;
import java.util.Objects;

final class Fingerprints {

    private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;
    private static final long XXHASH_PRIME_64 = 0xC2B2AE3D27D4EB4FL;

    private Fingerprints() {}

    static long contentsWithin(World world, Vector3 min, Vector3 max) {
        long sum = 0;
        for (World.Box box : world.boxes(min, max)) sum += ofBox(box);
        for (World.Link link : world.links(min, max)) sum += ofLink(link);
        return sum;
    }

    static long ofBox(World.Box box) {
        long hash = box.frame().position().hashCode() * 31L + box.frame().rotation().hashCode();
        hash = hash * 31L + box.size().hashCode();
        hash = hash * 31L + Objects.hashCode(box.label()) * 2L + (box.solid() ? 1 : 0);
        return hash * GOLDEN_RATIO_64;
    }

    static long ofLink(World.Link link) {
        long hash = link.from().hashCode() * 31L + link.to().hashCode();
        hash = hash * 31L + Objects.hashCode(link.label()) * 2L + (link.both() ? 1 : 0);
        return (hash + 7) * XXHASH_PRIME_64;
    }
}
