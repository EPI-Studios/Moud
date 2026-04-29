package com.moud.server.minestom.project;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moud.core.assets.ResPath;
import com.moud.net.protocol.ProjectCreate;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfo;
import com.moud.server.minestom.engine.MatchmakerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ProjectService {
    public static final String DEFAULT_PROJECT_FILE = "project.moud.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Path projectRoot;
    private final Path projectFile;

    public ProjectService(Path projectRoot) {
        this(projectRoot, DEFAULT_PROJECT_FILE);
    }

    public ProjectService(Path projectRoot, String projectFileName) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(projectFileName, "projectFileName");
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.projectFile = this.projectRoot.resolve(projectFileName).normalize();
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path projectFile() {
        return projectFile;
    }

    public MatchmakerConfig matchmakerConfig() {
        MatchmakerConfig defaults = MatchmakerConfig.defaults();
        ProjectFile file = loadIfExists();
        ProjectRuntimeSettings runtime = file == null ? null : file.runtime();
        if (runtime == null) {
            return defaults;
        }
        int players = runtime.playersPerInstance() == null ? defaults.playersPerInstance() : runtime.playersPerInstance();
        int maxInstances = runtime.maxInstances() == null ? defaults.maxInstances() : runtime.maxInstances();
        long emptyShutdown = runtime.emptyShutdownGraceMillis() == null ? defaults.emptyShutdownGraceMillis() : runtime.emptyShutdownGraceMillis();
        return new MatchmakerConfig(players, maxInstances, emptyShutdown, defaults.housekeepingIntervalMillis());
    }

    public ProjectRuntimeSettings runtimeSettings() {
        ProjectFile file = loadIfExists();
        return file == null ? null : file.runtime();
    }

    public ProjectInfo info(long requestId) {
        ProjectFile file = loadIfExists();
        if (file == null) {
            return new ProjectInfo(requestId, false, "", "");
        }
        String name = file.name() == null ? "" : file.name();
        String author = file.author() == null ? "" : file.author();
        return new ProjectInfo(requestId, true, name, author);
    }

    public ProjectCreateAck create(ProjectCreate request) {
        Objects.requireNonNull(request, "request");
        long requestId = request.requestId();

        if (Files.exists(projectFile)) {
            ProjectFile existing = loadIfExists();
            String name = existing == null || existing.name() == null ? "" : existing.name();
            String author = existing == null || existing.author() == null ? "" : existing.author();
            return new ProjectCreateAck(requestId, false, "Project already exists", name, author);
        }

        String name = normalizeName(request.name());
        if (name.isBlank()) {
            return new ProjectCreateAck(requestId, false, "Project name is required", "", "");
        }
        String author = normalizeAuthor(request.author());

        ProjectFile file = new ProjectFile(ProjectFile.FORMAT_V1, name, author);
        try {
            Files.createDirectories(projectRoot.resolve("scenes"));
            Files.createDirectories(projectRoot.resolve("assets"));
            Files.createDirectories(projectRoot.resolve("scripts"));

            writeProjectFileAtomic(file);
        } catch (Exception e) {
            return new ProjectCreateAck(requestId, false, e.getMessage(), name, author);
        }

        return new ProjectCreateAck(requestId, true, null, name, author);
    }

    public Path resolveProjectPath(String raw) {
        String normalized = normalizeProjectPath(raw);
        if (normalized.startsWith(ResPath.SCHEME)) {
            ResPath resPath = new ResPath(normalized);
            String inner = resPath.path();
            if (inner.startsWith("scripts/")) {
                Path legacy = projectRoot.resolve(inner);
                if (Files.isRegularFile(legacy)) {
                    return resolveSafe(legacy);
                }
                Path assets = projectRoot.resolve("assets").resolve(inner);
                return resolveSafe(assets);
            }
            return resolveSafe(projectRoot.resolve(resPath.path()));
        }
        return resolveSafe(projectRoot.resolve(normalized));
    }

    private Path resolveSafe(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(projectRoot)) {
            throw new IllegalArgumentException("Path escapes project root: " + candidate);
        }
        return normalized;
    }

    private static String normalizeProjectPath(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Path is required");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Path is required");
        }
        if (value.startsWith(ResPath.SCHEME)) {
            return value;
        }

        if (value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Backslashes are not allowed");
        }
        if (value.startsWith("/")) {
            throw new IllegalArgumentException("Absolute paths are not allowed");
        }
        if (value.indexOf(':') >= 0) {
            throw new IllegalArgumentException("':' is not allowed in project paths");
        }

        String[] parts = value.split("/");
        StringBuilder out = new StringBuilder(value.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Relative segments are not allowed");
            }
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(part);
        }
        return out.toString();
    }

    private ProjectFile loadIfExists() {
        if (!Files.exists(projectFile)) {
            return null;
        }
        try {
            String json = Files.readString(projectFile, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return null;
            }
            ProjectFile file = GSON.fromJson(json, ProjectFile.class);
            if (file == null) {
                return null;
            }
            int format = file.format();
            if (format != ProjectFile.FORMAT_V1) {
                throw new IllegalStateException("Unsupported project file format: " + format);
            }
            return file;
        } catch (Exception e) {
            System.err.println("[moud] failed to load project file '" + projectFile + "': " + e.getMessage());
            return null;
        }
    }

    private void writeProjectFileAtomic(ProjectFile file) throws Exception {
        String json = GSON.toJson(file);
        Path tmp = projectFile.resolveSibling(projectFile.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, projectFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            Files.move(tmp, projectFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        if (name.length() > 128) {
            name = name.substring(0, 128);
        }
        return name;
    }

    private static String normalizeAuthor(String raw) {
        if (raw == null) {
            return "";
        }
        String author = raw.trim();
        if (author.length() > 128) {
            author = author.substring(0, 128);
        }
        return author;
    }
}
