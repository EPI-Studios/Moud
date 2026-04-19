package com.moud.client.fabric.scripting;

import com.moud.client.fabric.editor.diagnostics.ClientOutput;
import com.moud.client.fabric.util.ClientDebugLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ClientLuauBridge {

    private final LuauRuntime runtime;
    private final LuauApiMapper apiMapper;

    ClientLuauBridge() {
        this.runtime   = new LuauRuntime();
        this.apiMapper = new LuauApiMapper(runtime);
    }

    static boolean isRuntimeLinked()   { return LuauRuntime.isLinked(); }
    static String  runtimeLinkProblem(){ return LuauRuntime.linkProblem(); }

    Program compile(Path scriptFile) {
        if (scriptFile == null) return null;
        try {
            if (!Files.isRegularFile(scriptFile)) {
                ClientDebugLog.error("ClientLuauBridge", "Script not found: " + scriptFile);
                return null;
            }
            long modified = Files.getLastModifiedTime(scriptFile).toMillis();
            String code = Files.readString(scriptFile, StandardCharsets.UTF_8);
            return new Program(scriptFile, modified, runtime.compile(code));
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "Compile error: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    Program compileSource(String name, String source) {
        if (source == null || source.isBlank()) return null;
        try {
            return new Program(null, source.hashCode(), runtime.compile(source), name);
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "Compile error [" + name + "]: " + e.getMessage(), e);
            return null;
        }
    }

    LuauVm createVm() {
        Object state = runtime.newState();
        try {
            runtime.openLibs(state);
            return new LuauVm(state, state);
        } catch (Exception e) {
            LuauRuntime.closeState(state);
            throw e;
        }
    }

    void sandboxVm(LuauVm vm) {
        try {
            runtime.sandbox(vm.state());
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "sandbox failed: " + e.getMessage(), e);
        }
    }

    static void closeState(Object state) { LuauRuntime.closeState(state); }

    int loadAndGetTableRef(Object thread, Program program) {
        String name = program.file() != null ? program.file().toString() : program.name();
        try {
            runtime.load(thread, name, program.bytecode());
            runtime.call(thread, 0, 1);
            if (!runtime.isTable(thread, -1)) {
                ClientDebugLog.error("ClientLuauBridge", "Script must return a table: " + name);
                runtime.top(thread, 0);
                return -1;
            }
            int ref = runtime.ref(thread, -1);
            runtime.top(thread, 0);
            return ref;
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "Load/exec error: " + name + ": " + e.getMessage(), e);
            runtime.top(thread, 0);
            return -1;
        }
    }

    int getFunctionRef(Object thread, int tableRef, String fieldName) {
        runtime.getRef(thread, tableRef);
        try {
            runtime.getField(thread, -1, fieldName);
            if (!runtime.isFunction(thread, -1)) return -1;
            return runtime.ref(thread, -1);
        } finally {
            runtime.top(thread, 0);
        }
    }

    void callMethod(Object thread, int tableRef, int funcRef) {
        if (funcRef < 0) return;
        try {
            runtime.getRef(thread, funcRef);
            runtime.getRef(thread, tableRef);
            runtime.call(thread, 1, 0);
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "Script error in method call: " + e.getMessage(), e);
        } finally {
            runtime.top(thread, 0);
        }
    }

    void callMethodWithDt(Object thread, int tableRef, int funcRef, double dt) {
        if (funcRef < 0) return;
        try {
            runtime.getRef(thread, funcRef);
            runtime.getRef(thread, tableRef);
            runtime.pushNumber(thread, dt);
            runtime.call(thread, 2, 0);
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "Script error in onFrame: " + e.getMessage(), e);
        } finally {
            runtime.top(thread, 0);
        }
    }

    void unref(Object thread, int ref) {
        if (ref >= 0) {
            try { runtime.unref(thread, ref); } catch (Exception ignored) {}
        }
    }

    void setApiGlobal(Object thread, String globalName, Object javaObject) {
        apiMapper.setApiGlobal(thread, globalName, javaObject);
    }

    void setNestedApiField(Object thread, String parentGlobal, String fieldName, Object javaObject) {
        try {
            runtime.getField(thread, -10002, parentGlobal);
            apiMapper.pushApiObject(thread, javaObject);
            runtime.setField(thread, -2, fieldName);
            runtime.pop(thread, 1);
        } catch (Exception e) {
            ClientDebugLog.error("ClientLuauBridge", "setNestedApiField failed: " + e.getMessage(), e);
        }
    }

    void installPrintRedirect(Object thread) {
        Object luaFunc = apiMapper.keepAlive(runtime.wrapFunction(callThread -> {
            int top = runtime.top(callThread);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= top; i++) {
                if (i > 1) sb.append('\t');
                if (runtime.isNoneOrNil(callThread, i)) {
                    sb.append("nil");
                } else if (runtime.isBoolean(callThread, i)) {
                    sb.append(runtime.toBoolean(callThread, i));
                } else if (runtime.isNumber(callThread, i)) {
                    double n = runtime.toNumber(callThread, i);
                    sb.append(n == Math.floor(n) && !Double.isInfinite(n) && Math.abs(n) < 1e15
                            ? (long) n : n);
                } else {
                    sb.append(runtime.toStringValue(callThread, i));
                }
            }
            String msg = sb.toString();
            ClientDebugLog.info("Script", msg);
            ClientOutput.print("Script", msg);
            return 0;
        }));
        runtime.pushFunction(thread, luaFunc);
        runtime.setGlobal(thread, "print");
    }

    record Program(Path file, long modifiedMs, byte[] bytecode, String name) {
        Program(Path file, long modifiedMs, byte[] bytecode) {
            this(file, modifiedMs, bytecode, file != null ? file.toString() : "<source>");
        }
    }

    record LuauVm(Object state, Object thread) {}
}
