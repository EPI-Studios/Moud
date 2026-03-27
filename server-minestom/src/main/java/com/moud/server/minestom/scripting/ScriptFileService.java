package com.moud.server.minestom.scripting;

import com.moud.core.assets.ResPath;
import com.moud.net.protocol.ScriptFileReadRequest;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ScriptFileWriteRequest;
import com.moud.server.minestom.project.ProjectService;
import com.moud.server.minestom.util.DebugLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ScriptFileService {
    private final ProjectService project;

    public ScriptFileService(ProjectService project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    public ScriptFileReadResponse read(String username, boolean editorEnabled, ScriptFileReadRequest request) {
        Objects.requireNonNull(request, "request");
        if (!editorEnabled) {
            return new ScriptFileReadResponse(
                    request.requestId(),
                    false,
                    request.path(),
                    "",
                    "editor disabled (MOUD_MODE=player)"
            );
        }

        String raw = request.path();
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            DebugLog.warn("script-files", "read rejected: empty path user=" + username);
            return new ScriptFileReadResponse(request.requestId(), false, raw, "", "Script path is required");
        }

        String allowedPath;
        try {
            allowedPath = normalizeAllowedScriptPath(path);
        } catch (Exception e) {
            DebugLog.warn("script-files", "read rejected path='" + path + "' user=" + username + ": " + e.getMessage());
            return new ScriptFileReadResponse(request.requestId(), false, raw, "", e.getMessage());
        }

        try {
            Path file = project.resolveProjectPath(allowedPath);
            if (!Files.isRegularFile(file)) {
                DebugLog.warn("script-files", "read not found path='" + allowedPath + "' user=" + username);
                return new ScriptFileReadResponse(request.requestId(), false, allowedPath, "", "Script not found: " + allowedPath);
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return new ScriptFileReadResponse(request.requestId(), true, allowedPath, content == null ? "" : content, null);
        } catch (Exception e) {
            DebugLog.error("script-files", "read failed path='" + allowedPath + "' user=" + username + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? "Read failed" : e.getMessage();
            return new ScriptFileReadResponse(request.requestId(), false, allowedPath, "", msg);
        }
    }

    public ScriptFileWriteAck write(String username, boolean editorEnabled, ScriptFileWriteRequest request) {
        Objects.requireNonNull(request, "request");
        if (!editorEnabled) {
            return new ScriptFileWriteAck(request.requestId(), false, request.path(), "editor disabled (MOUD_MODE=player)");
        }

        String raw = request.path();
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            DebugLog.warn("script-files", "write rejected: empty path user=" + username);
            return new ScriptFileWriteAck(request.requestId(), false, raw, "Script path is required");
        }

        String allowedPath;
        try {
            allowedPath = normalizeAllowedScriptPath(path);
        } catch (Exception e) {
            DebugLog.warn("script-files", "write rejected path='" + path + "' user=" + username + ": " + e.getMessage());
            return new ScriptFileWriteAck(request.requestId(), false, raw, e.getMessage());
        }

        String content = request.content() == null ? "" : request.content();
        try {
            Path file = project.resolveProjectPath(allowedPath);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            DebugLog.debug("script-files", "write ok path='" + allowedPath + "' bytes=" + content.length() + " user=" + username);
            return new ScriptFileWriteAck(request.requestId(), true, allowedPath, null);
        } catch (Exception e) {
            DebugLog.error("script-files", "write failed path='" + allowedPath + "' user=" + username + ": " + e.getMessage(), e);
            String msg = e.getMessage() == null ? "Write failed" : e.getMessage();
            return new ScriptFileWriteAck(request.requestId(), false, allowedPath, msg);
        }
    }

    private static String normalizeAllowedScriptPath(String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Script path is required");
        }
        if (path.startsWith(ResPath.SCHEME)) {
            ResPath rp = new ResPath(path);
            String inner = rp.path();
            if (!inner.startsWith("scripts/")) {
                throw new IllegalArgumentException("Only res://scripts/ paths are allowed");
            }
            return rp.value();
        }
        if (!path.startsWith("scripts/")) {
            throw new IllegalArgumentException("Only scripts/ paths are allowed");
        }
        return path;
    }
}

