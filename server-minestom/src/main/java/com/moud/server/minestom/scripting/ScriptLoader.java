package com.moud.server.minestom.scripting;

import com.moud.server.minestom.util.DebugLog;
import org.graalvm.polyglot.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class ScriptLoader {

    private static final String LOG_TAG = "script-runtime";

    private final Context ctx;
    private final Value createInstanceFn;
    private final Map<Path, Program> programs = new HashMap<>();

    ScriptLoader(Context ctx) {
        this.ctx = ctx;
        this.createInstanceFn = ctx.eval("js", "(proto) => Object.create(proto)");
    }

    Value createInstanceFn() {
        return createInstanceFn;
    }

    Program programFor(Path scriptFile) {
        if (scriptFile == null) return null;
        long modified;
        try {
            modified = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (Exception e) {
            return null;
        }
        Program cached = programs.get(scriptFile);
        if (cached != null && cached.modifiedMs() == modified) return cached;
        Program loaded = loadProgram(scriptFile, modified);
        if (loaded != null) programs.put(scriptFile, loaded);
        return loaded;
    }

    Value createNodeInstance(Value exports) {
        if (exports == null) return null;
        try {
            if (exports.canInstantiate()) return exports.newInstance();
            if (exports.canExecute()) {
                Value v = exports.execute();
                if (v != null) return v;
            }
            if (exports.hasMembers()) return createInstanceFn.execute(exports);
        } catch (PolyglotException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        throw new IllegalStateException("Script must evaluate to an object or a class/constructor");
    }

    private Program loadProgram(Path scriptFile, long modifiedMs) {
        try {
            if (!Files.isRegularFile(scriptFile)) return null;
            String code = Files.readString(scriptFile, StandardCharsets.UTF_8);
            Source source = Source.newBuilder("js", code, scriptFile.toString()).build();
            Value exports = ctx.eval(source);
            if (exports == null) return null;
            return new Program(scriptFile, modifiedMs, exports);
        } catch (PolyglotException e) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            DebugLog.error(LOG_TAG, "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    record Program(Path file, long modifiedMs, Value exports) {}
}
