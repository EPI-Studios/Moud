package com.moud.bootstrap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

final class LoaderCompatibility {

    private LoaderCompatibility() {
    }

    static void assertMinimumFabricLoader(String minimumVersion) {
        String actualVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");

        if (!isAtLeast(actualVersion, minimumVersion)) {
            throw new IllegalStateException(
                    "Moud requires Fabric Loader " + minimumVersion + "+, found " + actualVersion);
        }
    }

    static boolean isAtLeast(String actualVersion, String minimumVersion) {
        try {
            Version actual = Version.parse(actualVersion);
            Version minimum = Version.parse(minimumVersion);
            return actual.compareTo(minimum) >= 0;
        } catch (VersionParsingException e) {
            throw new IllegalArgumentException(
                    "Could not parse Fabric Loader version comparison: actual="
                            + actualVersion + ", minimum=" + minimumVersion, e);
        }
    }
}
