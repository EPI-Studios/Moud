package com.moud.client.util;

import com.moud.api.util.PathUtils;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class IdentifierUtils {
    private IdentifierUtils() {
    }

    public static String normalizeAssetPathKey(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String normalized = PathUtils.normalizeSlashes(rawPath.trim()).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        return normalized;
    }

    public static Identifier resolveMoudIdentifier(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = PathUtils.normalizeSlashes(rawPath.trim()).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("moud:moud/")) {
            normalized = "moud:" + normalized.substring("moud:moud/".length());
        }
        if (!normalized.contains(":")) {
            normalized = normalized.startsWith("moud/") ? "moud:" + normalized.substring(5) : "moud:" + normalized;
        }
        if (normalized.startsWith("moud:/")) {
            normalized = "moud:" + normalized.substring("moud:/".length());
        }
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null) {
            return null;
        }
        if ("moud".equals(identifier.getNamespace())) {
            String path = identifier.getPath();
            while (path.startsWith("moud/")) {
                path = path.substring(5);
            }
            if (path.isEmpty()) {
                return null;
            }
            return Identifier.of("moud", path);
        }
        return identifier;
    }

    public static Identifier resolveModelIdentifier(String rawPath) {
        return resolveMoudIdentifier(rawPath);
    }

    public static Identifier resolveTextureIdentifier(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = normalizeAssetPathKey(rawPath);
        if (normalized.startsWith("moud:moud/")) {
            normalized = "moud:" + normalized.substring("moud:moud/".length());
        }
        if (!normalized.contains(":")) {
            String effective = normalized;
            if (normalized.contains("/")) {
                int firstSlash = normalized.indexOf('/');
                String maybeNamespace = normalized.substring(0, firstSlash);
                String remainder = normalized.substring(firstSlash + 1);
                if (!maybeNamespace.isBlank() && !remainder.isBlank()) {
                    effective = maybeNamespace + ":" + remainder;
                }
            }
            if (!effective.contains(":")) {
                effective = effective.startsWith("moud/") ? "moud:" + effective.substring(5) : "moud:" + effective;
            }
            normalized = effective;
        }
        Identifier parsed = Identifier.tryParse(normalized);
        if (parsed != null && "moud".equals(parsed.getNamespace())) {
            String path = parsed.getPath();
            if (path.startsWith("moud/") && path.length() > 5) {
                return Identifier.of("moud", path.substring(5));
            }
        }
        return parsed;
    }
}
