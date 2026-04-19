package com.moud.server.minestom.scripts;

import com.moud.core.assets.ResPath;
import com.moud.net.protocol.AssetPathOp;
import com.moud.net.protocol.AssetPathOpAck;
import com.moud.server.minestom.util.DebugLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AssetPathOpsService {
    private static final String RES_PREFIX = ResPath.SCHEME;

    private final Path projectRoot;
    private final SceneReferenceRewriter rewriter;

    public AssetPathOpsService(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.rewriter = new SceneReferenceRewriter(projectRoot);
    }

    public AssetPathOpAck apply(AssetPathOp op) {
        if (op == null) return null;
        return switch (op.kind()) {
            case CREATE_FOLDER -> createFolder(op);
            case RENAME -> rename(op);
            case DELETE_RECURSIVE -> deleteRecursive(op);
        };
    }

    private AssetPathOpAck createFolder(AssetPathOp op) {
        String rel = stripScheme(op.path());
        if (!isAllowed(rel)) return fail(op, "Folder path must be under scripts/ or assets/");
        Path target = projectRoot.resolve(rel).normalize();
        if (!target.startsWith(projectRoot)) return fail(op, "Path escapes project root");
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            return fail(op, e.getMessage());
        }
        return new AssetPathOpAck(op.requestId(), op.kind(), true, op.path(), op.path(), 0, "");
    }

    private AssetPathOpAck rename(AssetPathOp op) {
        String oldRel = stripScheme(op.path());
        String newRel = stripScheme(op.newPath());
        if (!isAllowed(oldRel) || !isAllowed(newRel)) return fail(op, "Rename restricted to scripts/ or assets/");
        Path source = projectRoot.resolve(oldRel).normalize();
        Path target = projectRoot.resolve(newRel).normalize();
        if (!source.startsWith(projectRoot) || !target.startsWith(projectRoot)) return fail(op, "Path escapes project root");
        if (!Files.exists(source)) return fail(op, "Source not found");
        if (Files.exists(target)) return fail(op, "Target already exists");

        boolean isDirectory = Files.isDirectory(source);
        Map<String, String> fileRewrites = isDirectory
                ? collectChildRewrites(source, oldRel, newRel)
                : Map.of(oldRel, newRel);

        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target);
            } catch (IOException e) {
                return fail(op, e.getMessage());
            }
        }

        int scenes = isDirectory
                ? rewriter.rewritePrefix(oldRel + "/", newRel + "/")
                  + rewriter.rewritePrefix(RES_PREFIX + oldRel + "/", RES_PREFIX + newRel + "/")
                : rewriter.rewriteMany(withResVariants(fileRewrites));

        return new AssetPathOpAck(op.requestId(), op.kind(), true, op.path(), op.newPath(), scenes, "");
    }

    private AssetPathOpAck deleteRecursive(AssetPathOp op) {
        String rel = stripScheme(op.path());
        if (!isAllowed(rel)) return fail(op, "Delete restricted to scripts/ or assets/");
        Path target = projectRoot.resolve(rel).normalize();
        if (!target.startsWith(projectRoot)) return fail(op, "Path escapes project root");
        if (!Files.exists(target)) return fail(op, "Not found");

        boolean isDirectory = Files.isDirectory(target);
        try {
            deleteTree(target);
        } catch (IOException e) {
            return fail(op, e.getMessage());
        }

        int scenes = isDirectory
                ? rewriter.rewritePrefix(rel + "/", "")
                  + rewriter.rewritePrefix(RES_PREFIX + rel + "/", "")
                : rewriter.rewriteExact(rel, "") + rewriter.rewriteExact(RES_PREFIX + rel, "");

        return new AssetPathOpAck(op.requestId(), op.kind(), true, op.path(), "", scenes, "");
    }

    private Map<String, String> collectChildRewrites(Path source, String oldRel, String newRel) {
        Map<String, String> rewrites = new HashMap<>();
        rewrites.put(oldRel, newRel);
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String rel = toForwardSlash(projectRoot.relativize(file));
                String mapped = newRel + rel.substring(oldRel.length());
                rewrites.put(rel, mapped);
            });
        } catch (IOException ignored) {
        }
        return rewrites;
    }

    private static Map<String, String> withResVariants(Map<String, String> rewrites) {
        Map<String, String> out = new HashMap<>(rewrites.size() * 2);
        for (Map.Entry<String, String> e : rewrites.entrySet()) {
            out.put(e.getKey(), e.getValue());
            out.put(RES_PREFIX + e.getKey(), RES_PREFIX + e.getValue());
        }
        return out;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(root)) {
            entries = new ArrayList<>(walk.toList());
        }
        entries.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());
        for (Path entry : entries) {
            Files.deleteIfExists(entry);
        }
    }

    private static AssetPathOpAck fail(AssetPathOp op, String error) {
        DebugLog.error("asset-path-ops", op.kind() + " failed: " + error);
        return new AssetPathOpAck(op.requestId(), op.kind(), false, op.path(), op.newPath(), 0,
                error == null ? "failed" : error);
    }

    private static String stripScheme(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        if (trimmed.startsWith(RES_PREFIX)) return trimmed.substring(RES_PREFIX.length());
        return trimmed;
    }

    private static boolean isAllowed(String rel) {
        return rel != null && (rel.startsWith("scripts/") || rel.startsWith("scripts")
                || rel.startsWith("assets/") || rel.equals("scripts") || rel.equals("assets"));
    }

    private static String toForwardSlash(Path path) {
        return path.toString().replace('\\', '/');
    }
}
