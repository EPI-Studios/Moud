package com.moud.server.minestom.scripting;

import com.moud.server.minestom.util.DebugLog;
import org.graalvm.polyglot.HostAccess;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

final class LuauRuntimeBridge implements AutoCloseable {
    private static final String LUA_STATE_CLASS = "net.hollowcube.luau.LuaState";
    private static final String LUA_FUNC_CLASS = "net.hollowcube.luau.LuaFunc";
    private static final String LUA_COMPILER_CLASS = "net.hollowcube.luau.compiler.LuauCompiler";
    private static final String LUA_COMPILE_EXCEPTION_CLASS = "net.hollowcube.luau.compiler.LuauCompileException";
    private static volatile boolean runtimeLinkChecked;
    private static volatile String runtimeLinkProblem;

    private final LuauReflection reflection;
    private final ArrayList<Closeable> closeables = new ArrayList<>();
    private final HashMap<Path, Program> programs = new HashMap<>();
    private final ConcurrentHashMap<Class<?>, BoundType> boundTypes = new ConcurrentHashMap<>();

    LuauRuntimeBridge() {
        this.reflection = new LuauReflection();
    }

    static boolean isRuntimeLinked() {
        return runtimeLinkProblem() == null;
    }

    static String runtimeLinkProblem() {
        if (runtimeLinkChecked) {
            return runtimeLinkProblem;
        }
        synchronized (LuauRuntimeBridge.class) {
            if (runtimeLinkChecked) {
                return runtimeLinkProblem;
            }
            try {
                Class.forName(LUA_STATE_CLASS);
                Class.forName(LUA_FUNC_CLASS);
                Class.forName(LUA_COMPILER_CLASS);
                Class.forName(LUA_COMPILE_EXCEPTION_CLASS);
                runtimeLinkProblem = null;
            } catch (Throwable t) {
                boolean classpathHasLuau = System.getProperty("java.class.path", "").contains("luau-");
                runtimeLinkProblem = "java=" + Runtime.version().feature()
                        + " classpathHasLuau=" + classpathHasLuau
                        + " cause=" + describeThrowableChain(t);
            }
            runtimeLinkChecked = true;
            return runtimeLinkProblem;
        }
    }

    private static String describeThrowableChain(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            String type = current.getClass().getSimpleName();
            String message = current.getMessage();
            sb.append(type);
            if (message != null && !message.isBlank()) {
                sb.append(": ").append(message);
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    Program programFor(Path scriptFile) {
        if (scriptFile == null) {
            return null;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (Exception e) {
            return null;
        }
        Program cached = programs.get(scriptFile);
        if (cached != null && cached.modifiedMs() == modified) {
            return cached;
        }
        Program loaded = loadProgram(scriptFile, modified);
        if (loaded != null) {
            programs.put(scriptFile, loaded);
            DebugLog.info("script-runtime", (cached == null ? "loaded" : "reloaded")
                    + " language=luau file=" + scriptFile.toAbsolutePath().normalize());
        }
        return loaded;
    }

    ScriptObject createNodeInstance(Program program) throws ScriptInvocationException {
        return createNodeInstance(program, null);
    }

    ScriptObject createNodeInstance(Program program, Object cachedApiTarget) throws ScriptInvocationException {
        if (program == null) {
            throw new ScriptInvocationException("Missing Luau program");
        }
        LuauVm vm = createVm();
        Object thread = vm.thread();
        try {
            reflection.load(thread, program.file().toString(), program.bytecode());
            reflection.call(thread, 0, 1);
            if (!reflection.isTable(thread, -1)) {
                throw new ScriptInvocationException("Luau script must return a table");
            }
            int instanceRef = reflection.ref(thread, -1);
            reflection.top(thread, 0);
            IdentityHashMap<Object, Integer> boundCache = new IdentityHashMap<>();
            if (cachedApiTarget != null) {
                pushValue(thread, cachedApiTarget, boundCache);
                int apiRef = reflection.ref(thread, -1);
                reflection.top(thread, 0);
                boundCache.put(cachedApiTarget, apiRef);
            }
            return new LuauScriptObject(vm.state(), thread, instanceRef, boundCache);
        } catch (ScriptInvocationException e) {
            closeState(vm.state());
            throw e;
        } catch (Exception e) {
            closeState(vm.state());
            throw new ScriptInvocationException(e.getMessage(), e);
        }
    }

    ToolExports loadToolExports(Program program) throws ScriptInvocationException {
        if (program == null) {
            throw new ScriptInvocationException("Missing Luau program");
        }
        LuauVm vm = createVm();
        Object thread = vm.thread();
        try {
            reflection.load(thread, program.file().toString(), program.bytecode());
            reflection.call(thread, 0, 1);
            if (!reflection.isTable(thread, -1)) {
                throw new ScriptInvocationException("Luau tool script must return a table");
            }
            int exportsRef = reflection.ref(thread, -1);
            reflection.top(thread, 0);
            boolean tool = readToolFlag(thread, exportsRef);
            int actionsRef = readActionsRef(thread, exportsRef);
            return new ToolExports(vm.state(), thread, exportsRef, actionsRef, tool);
        } catch (ScriptInvocationException e) {
            closeState(vm.state());
            throw e;
        } catch (Exception e) {
            closeState(vm.state());
            throw new ScriptInvocationException(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        closeables.clear();
    }

    private Program loadProgram(Path scriptFile, long modifiedMs) {
        try {
            if (!Files.isRegularFile(scriptFile)) {
                return null;
            }
            String code = Files.readString(scriptFile, StandardCharsets.UTF_8);
            byte[] bytecode = reflection.compile(code);
            return new Program(scriptFile, modifiedMs, bytecode);
        } catch (Exception e) {
            DebugLog.error("script-runtime", "load failed: " + scriptFile + ": " + e.getMessage(), e);
            return null;
        }
    }

    private LuauVm createVm() {
        Object state = reflection.newState();
        try {
            reflection.openLibs(state);
            reflection.sandbox(state);
            Object thread = reflection.newThread(state);
            reflection.sandboxThread(thread);
            reflection.pop(state, 1);
            return new LuauVm(state, thread);
        } catch (Exception e) {
            closeState(state);
            throw e;
        }
    }

    private boolean readToolFlag(Object thread, int exportsRef) {
        reflection.getRef(thread, exportsRef);
        try {
            reflection.getField(thread, -1, "tool");
            return reflection.isBoolean(thread, -1) && reflection.toBoolean(thread, -1);
        } finally {
            reflection.top(thread, 0);
        }
    }

    private int readActionsRef(Object thread, int exportsRef) {
        reflection.getRef(thread, exportsRef);
        try {
            reflection.getField(thread, -1, "actions");
            if (!reflection.isTable(thread, -1)) {
                return 0;
            }
            return reflection.ref(thread, -1);
        } finally {
            reflection.top(thread, 0);
        }
    }

    private List<String> listActions(Object thread, int actionsRef) {
        if (actionsRef == 0) {
            return List.of();
        }
        ArrayList<String> actions = new ArrayList<>();
        reflection.getRef(thread, actionsRef);
        try {
            reflection.pushNil(thread);
            while (reflection.next(thread, -2)) {
                try {
                    if (reflection.isString(thread, -2) && reflection.isFunction(thread, -1)) {
                        String key = reflection.toString(thread, -2);
                        if (key != null && !key.isBlank()) {
                            actions.add(key);
                        }
                    }
                } finally {
                    reflection.pop(thread, 1);
                }
            }
        } finally {
            reflection.pop(thread, 1);
        }
        actions.sort(Comparator.naturalOrder());
        return List.copyOf(actions);
    }

    private void invokeAction(Object thread, int actionsRef, String action, Object api) throws ScriptInvocationException {
        if (actionsRef == 0 || action == null || action.isBlank()) {
            throw new ScriptInvocationException("Unknown action: " + action);
        }
        reflection.getRef(thread, actionsRef);
        try {
            reflection.getField(thread, -1, action);
            if (!reflection.isFunction(thread, -1)) {
                throw new ScriptInvocationException("Unknown action: " + action);
            }
            pushValue(thread, api, new IdentityHashMap<>());
            reflection.call(thread, 1, 0);
        } catch (Exception e) {
            throw scriptException(e);
        } finally {
            int top = reflection.top(thread);
            if (top > 0) {
                reflection.top(thread, 0);
            }
        }
    }

    private void pushValue(Object thread, Object value, IdentityHashMap<Object, Integer> boundCache) {
        if (value == null) {
            reflection.pushNil(thread);
            return;
        }
        if (value instanceof Boolean b) {
            reflection.pushBoolean(thread, b);
            return;
        }
        if (value instanceof Byte n) {
            reflection.pushInteger(thread, n.intValue());
            return;
        }
        if (value instanceof Short n) {
            reflection.pushInteger(thread, n.intValue());
            return;
        }
        if (value instanceof Integer n) {
            reflection.pushInteger(thread, n);
            return;
        }
        if (value instanceof Long n) {
            reflection.pushNumber(thread, n.doubleValue());
            return;
        }
        if (value instanceof Float n) {
            reflection.pushNumber(thread, n.doubleValue());
            return;
        }
        if (value instanceof Double n) {
            reflection.pushNumber(thread, n);
            return;
        }
        if (value instanceof String s) {
            reflection.pushString(thread, s);
            return;
        }
        if (value instanceof Character c) {
            reflection.pushString(thread, String.valueOf(c));
            return;
        }
        if (value instanceof ScriptCallable callable) {
            if (callable instanceof LuauCallableRef ref) {
                ref.push(thread);
            } else {
                reflection.pushNil(thread);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            reflection.newTable(thread);
            for (int i = 0; i < length; i++) {
                pushValue(thread, Array.get(value, i), boundCache);
                reflection.rawSetI(thread, -2, i + 1);
            }
            return;
        }
        if (value instanceof List<?> list) {
            reflection.newTable(thread);
            for (int i = 0; i < list.size(); i++) {
                pushValue(thread, list.get(i), boundCache);
                reflection.rawSetI(thread, -2, i + 1);
            }
            return;
        }

        Integer cachedRef = boundCache.get(value);
        if (cachedRef != null && cachedRef > 0) {
            reflection.getRef(thread, cachedRef);
            return;
        }

        BoundType boundType = boundTypes.computeIfAbsent(value.getClass(), this::buildBoundType);
        if (boundType.methods.isEmpty()) {
            reflection.pushNil(thread);
            return;
        }

        reflection.newTable(thread);
        for (Map.Entry<String, List<BoundMethod>> entry : boundType.methods.entrySet()) {
            Object func = reflection.wrapFunction(state -> invokeBoundMethod(value, entry.getValue(), state));
            closeables.add((Closeable) func);
            reflection.pushFunction(thread, func);
            reflection.setField(thread, -2, entry.getKey());
        }
    }

    private int invokeBoundMethod(Object target, List<BoundMethod> methods, Object thread) {
        try {
            int top = reflection.top(thread);
            BoundMethod chosen = null;
            Object[] args = null;
            for (BoundMethod method : methods) {
                args = method.tryConvert(thread, top);
                if (args != null) {
                    chosen = method;
                    break;
                }
            }
            if (chosen == null || args == null) {
                throw new IllegalArgumentException("No matching overload for Luau-bound method");
            }
            Object result = chosen.method.invoke(target, args);
            return pushReturnValue(thread, result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(cause.getMessage(), cause);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private int pushReturnValue(Object thread, Object result) {
        if (result == null) {
            return 0;
        }
        pushValue(thread, result, new IdentityHashMap<>());
        return 1;
    }

    private BoundType buildBoundType(Class<?> type) {
        HashMap<String, List<BoundMethod>> methods = new HashMap<>();
        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(HostAccess.Export.class)) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            method.setAccessible(true);
            methods.computeIfAbsent(method.getName(), ignored -> new ArrayList<>())
                    .add(new BoundMethod(method, reflection));
        }
        for (List<BoundMethod> overloads : methods.values()) {
            overloads.sort(Comparator.comparingInt((BoundMethod m) -> m.requiredArgs).thenComparingInt(m -> m.paramTypes.length));
        }
        return new BoundType(Map.copyOf(methods));
    }

    private static ScriptInvocationException scriptException(Exception e) {
        if (e instanceof ScriptInvocationException se) {
            return se;
        }
        Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
        return new ScriptInvocationException(cause.getMessage(), cause);
    }

    private static void closeState(Object state) {
        if (state instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }

    record Program(Path file, long modifiedMs, byte[] bytecode) {
    }

    private record LuauVm(Object state, Object thread) {
    }

    final class ToolExports implements AutoCloseable {
        private final Object state;
        private final Object thread;
        private final int exportsRef;
        private final int actionsRef;
        private final boolean tool;

        private ToolExports(Object state, Object thread, int exportsRef, int actionsRef, boolean tool) {
            this.state = state;
            this.thread = thread;
            this.exportsRef = exportsRef;
            this.actionsRef = actionsRef;
            this.tool = tool;
        }

        boolean tool() {
            return tool;
        }

        List<String> listActions() {
            return LuauRuntimeBridge.this.listActions(thread, actionsRef);
        }

        void invokeAction(String action, Object api) throws ScriptInvocationException {
            LuauRuntimeBridge.this.invokeAction(thread, actionsRef, action, api);
        }

        @Override
        public void close() {
            if (actionsRef > 0) {
                reflection.unref(thread, actionsRef);
            }
            if (exportsRef > 0) {
                reflection.unref(thread, exportsRef);
            }
        }
    }

    private record BoundType(Map<String, List<BoundMethod>> methods) {
    }

    private static final class BoundMethod {
        private final Method method;
        private final LuauReflection reflection;
        private final Class<?>[] paramTypes;
        private final boolean varArgs;
        private final int requiredArgs;

        private BoundMethod(Method method, LuauReflection reflection) {
            this.method = method;
            this.reflection = reflection;
            this.paramTypes = method.getParameterTypes();
            this.varArgs = method.isVarArgs();
            this.requiredArgs = varArgs ? paramTypes.length - 1 : paramTypes.length;
        }

        private Object[] tryConvert(Object thread, int actualArgs) {
            if (!varArgs && actualArgs != paramTypes.length) {
                return null;
            }
            if (varArgs && actualArgs < requiredArgs) {
                return null;
            }
            Object[] converted = new Object[paramTypes.length];
            int stackIndex = 1;
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (varArgs && i == paramTypes.length - 1) {
                    Class<?> componentType = type.getComponentType();
                    int count = actualArgs - requiredArgs;
                    Object array = Array.newInstance(componentType, count);
                    for (int j = 0; j < count; j++) {
                        Object value = reflection.readValue(thread, stackIndex++, componentType, true);
                        if (value == LuauReflection.INVALID) {
                            return null;
                        }
                        Array.set(array, j, value);
                    }
                    converted[i] = array;
                    return converted;
                }
                Object value = reflection.readValue(thread, stackIndex++, type, false);
                if (value == LuauReflection.INVALID) {
                    return null;
                }
                converted[i] = value;
            }
            return stackIndex - 1 == actualArgs ? converted : null;
        }
    }

    private final class LuauScriptObject implements ScriptObject {
        private final Object state;
        private final Object thread;
        private final int instanceRef;
        private final IdentityHashMap<Object, Integer> boundCache;

        private LuauScriptObject(Object state, Object thread, int instanceRef, IdentityHashMap<Object, Integer> boundCache) {
            this.state = state;
            this.thread = thread;
            this.instanceRef = instanceRef;
            this.boundCache = boundCache;
        }

        @Override
        public boolean hasMethod(String member) {
            if (member == null || member.isBlank()) {
                return false;
            }
            reflection.getRef(thread, instanceRef);
            try {
                reflection.getField(thread, -1, member);
                return reflection.isFunction(thread, -1);
            } finally {
                reflection.top(thread, 0);
            }
        }

        @Override
        public void invokeMethod(String member, Object... args) throws ScriptInvocationException {
            if (member == null || member.isBlank()) {
                return;
            }
            reflection.getRef(thread, instanceRef);
            try {
                reflection.getField(thread, -1, member);
                if (!reflection.isFunction(thread, -1)) {
                    return;
                }
                reflection.getRef(thread, instanceRef);
                for (Object arg : args) {
                    pushValue(thread, arg, boundCache);
                }
                reflection.call(thread, args.length + 1, 0);
            } catch (Exception e) {
                throw scriptException(e);
            } finally {
                reflection.top(thread, 0);
            }
        }

        @Override
        public void close() {
            for (Integer ref : boundCache.values()) {
                if (ref != null && ref > 0) {
                    reflection.unref(thread, ref);
                }
            }
            reflection.unref(thread, instanceRef);
            // the runtime scripts hit ALWAYS the lua_close() callback issue
            // thats why i made it so it keep refs clean and let the jvm reclaim the states that are preinstance instead of
            // crashing the entire server when refreshing
        }
    }

    private static final class LuauCallableRef implements ScriptCallable {
        private final LuauReflection reflection;
        private final Object thread;
        private final int ref;

        private LuauCallableRef(LuauReflection reflection, Object thread, int ref) {
            this.reflection = reflection;
            this.thread = thread;
            this.ref = ref;
        }

        @Override
        public void invoke() throws ScriptInvocationException {
            try {
                reflection.getRef(thread, ref);
                reflection.call(thread, 0, 0);
            } catch (Exception e) {
                throw scriptException(e);
            } finally {
                reflection.top(thread, 0);
            }
        }

        private void push(Object targetThread) {
            if (!Objects.equals(targetThread, thread)) {
                throw new IllegalStateException("Luau callable belongs to a different VM thread");
            }
            reflection.getRef(thread, ref);
        }
    }

    private final class LuauReflection {
        private static final Object INVALID = new Object();

        private final Class<?> luaStateClass;
        private final Field compilerDefault;
        private final Method luaStateNewState;
        private final Method stateClose;
        private final Method stateOpenLibs;
        private final Method stateSandbox;
        private final Method stateNewThread;
        private final Method stateSandboxThread;
        private final Method statePop;
        private final Method stateTopGet;
        private final Method stateTopSet;
        private final Method statePushValue;
        private final Method stateLoad;
        private final Method stateCall;
        private final Method stateIsTable;
        private final Method stateIsFunction;
        private final Method stateIsBoolean;
        private final Method stateIsNumber;
        private final Method stateIsString;
        private final Method stateIsNil;
        private final Method stateIsNoneOrNil;
        private final Method stateLen;
        private final Method stateRawGetI;
        private final Method stateGetField;
        private final Method stateNext;
        private final Method statePushNil;
        private final Method statePushBoolean;
        private final Method statePushNumber;
        private final Method statePushInteger;
        private final Method statePushString;
        private final Method stateNewTable;
        private final Method stateRawSetI;
        private final Method stateSetField;
        private final Method statePushFunction;
        private final Method stateToBoolean;
        private final Method stateToNumber;
        private final Method stateToIntegerOrNull;
        private final Method stateToString;
        private final Method stateRef;
        private final Method stateUnref;
        private final Method stateGetRef;
        private final Method luaFuncWrap;
        private final Method compilerCompileString;

        private LuauReflection() {
            try {
                luaStateClass = Class.forName(LUA_STATE_CLASS);
                Class<?> luaFuncClass = Class.forName(LUA_FUNC_CLASS);
                Class<?> compilerClass = Class.forName(LUA_COMPILER_CLASS);
                Class<?> builtinLibraryArray = Class.forName("net.hollowcube.luau.BuilinLibrary").arrayType();

                compilerDefault = compilerClass.getField("DEFAULT");
                luaStateNewState = luaStateClass.getMethod("newState");
                stateClose = luaStateClass.getMethod("close");
                stateOpenLibs = luaStateClass.getMethod("openLibs", builtinLibraryArray);
                stateSandbox = luaStateClass.getMethod("sandbox");
                stateNewThread = luaStateClass.getMethod("newThread");
                stateSandboxThread = luaStateClass.getMethod("sandboxThread");
                statePop = luaStateClass.getMethod("pop", int.class);
                stateTopGet = luaStateClass.getMethod("top");
                stateTopSet = luaStateClass.getMethod("top", int.class);
                statePushValue = luaStateClass.getMethod("pushValue", int.class);
                stateLoad = luaStateClass.getMethod("load", String.class, byte[].class);
                stateCall = luaStateClass.getMethod("call", int.class, int.class);
                stateIsTable = luaStateClass.getMethod("isTable", int.class);
                stateIsFunction = luaStateClass.getMethod("isFunction", int.class);
                stateIsBoolean = luaStateClass.getMethod("isBoolean", int.class);
                stateIsNumber = luaStateClass.getMethod("isNumber", int.class);
                stateIsString = luaStateClass.getMethod("isString", int.class);
                stateIsNil = luaStateClass.getMethod("isNil", int.class);
                stateIsNoneOrNil = luaStateClass.getMethod("isNoneOrNil", int.class);
                stateLen = luaStateClass.getMethod("len", int.class);
                stateRawGetI = luaStateClass.getMethod("rawGetI", int.class, int.class);
                stateGetField = luaStateClass.getMethod("getField", int.class, String.class);
                stateNext = luaStateClass.getMethod("next", int.class);
                statePushNil = luaStateClass.getMethod("pushNil");
                statePushBoolean = luaStateClass.getMethod("pushBoolean", boolean.class);
                statePushNumber = luaStateClass.getMethod("pushNumber", double.class);
                statePushInteger = luaStateClass.getMethod("pushInteger", int.class);
                statePushString = luaStateClass.getMethod("pushString", String.class);
                stateNewTable = luaStateClass.getMethod("newTable");
                stateRawSetI = luaStateClass.getMethod("rawSetI", int.class, int.class);
                stateSetField = luaStateClass.getMethod("setField", int.class, String.class);
                statePushFunction = luaStateClass.getMethod("pushFunction", luaFuncClass);
                stateToBoolean = luaStateClass.getMethod("toBoolean", int.class);
                stateToNumber = luaStateClass.getMethod("toNumber", int.class);
                stateToIntegerOrNull = luaStateClass.getMethod("toIntegerOrNull", int.class);
                stateToString = luaStateClass.getMethod("toString", int.class);
                stateRef = luaStateClass.getMethod("ref", int.class);
                stateUnref = luaStateClass.getMethod("unref", int.class);
                stateGetRef = luaStateClass.getMethod("getRef", int.class);
                luaFuncWrap = luaFuncClass.getMethod("wrap", ToIntFunction.class, String.class);
                compilerCompileString = compilerClass.getMethod("compile", String.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize Luau reflection bridge", e);
            }
        }

        private Object newState() {
            return invokeStatic(luaStateNewState, null);
        }

        private byte[] compile(String code) {
            Object compiler = getField(compilerDefault, null);
            return (byte[]) invoke(compilerCompileString, compiler, code);
        }

        private void openLibs(Object state) {
            Object empty = Array.newInstance(stateOpenLibs.getParameterTypes()[0].componentType(), 0);
            invoke(stateOpenLibs, state, empty);
        }

        private void sandbox(Object state) {
            invoke(stateSandbox, state);
        }

        private Object newThread(Object state) {
            return invoke(stateNewThread, state);
        }

        private void sandboxThread(Object state) {
            invoke(stateSandboxThread, state);
        }

        private void pop(Object state, int count) {
            invoke(statePop, state, count);
        }

        private int top(Object state) {
            return (Integer) invoke(stateTopGet, state);
        }

        private void top(Object state, int count) {
            invoke(stateTopSet, state, count);
        }

        private void pushValue(Object state, int index) {
            invoke(statePushValue, state, index);
        }

        private void load(Object state, String chunkName, byte[] data) {
            invoke(stateLoad, state, chunkName, data);
        }

        private void call(Object state, int nargs, int nresults) {
            invoke(stateCall, state, nargs, nresults);
        }

        private boolean isTable(Object state, int index) {
            return (Boolean) invoke(stateIsTable, state, index);
        }

        private boolean isFunction(Object state, int index) {
            return (Boolean) invoke(stateIsFunction, state, index);
        }

        private boolean isBoolean(Object state, int index) {
            return (Boolean) invoke(stateIsBoolean, state, index);
        }

        private boolean isNumber(Object state, int index) {
            return (Boolean) invoke(stateIsNumber, state, index);
        }

        private boolean isString(Object state, int index) {
            return (Boolean) invoke(stateIsString, state, index);
        }

        private boolean isNil(Object state, int index) {
            return (Boolean) invoke(stateIsNil, state, index);
        }

        private boolean isNoneOrNil(Object state, int index) {
            return (Boolean) invoke(stateIsNoneOrNil, state, index);
        }

        private int len(Object state, int index) {
            return (Integer) invoke(stateLen, state, index);
        }

        private void rawGetI(Object state, int index, int key) {
            invoke(stateRawGetI, state, index, key);
        }

        private void getField(Object state, int index, String key) {
            invoke(stateGetField, state, index, key);
        }

        private boolean next(Object state, int index) {
            return (Boolean) invoke(stateNext, state, index);
        }

        private void pushNil(Object state) {
            invoke(statePushNil, state);
        }

        private void pushBoolean(Object state, boolean value) {
            invoke(statePushBoolean, state, value);
        }

        private void pushNumber(Object state, double value) {
            invoke(statePushNumber, state, value);
        }

        private void pushInteger(Object state, int value) {
            invoke(statePushInteger, state, value);
        }

        private void pushString(Object state, String value) {
            invoke(statePushString, state, value);
        }

        private void newTable(Object state) {
            invoke(stateNewTable, state);
        }

        private void rawSetI(Object state, int index, int key) {
            invoke(stateRawSetI, state, index, key);
        }

        private void setField(Object state, int index, String key) {
            invoke(stateSetField, state, index, key);
        }

        private void pushFunction(Object state, Object func) {
            invoke(statePushFunction, state, func);
        }

        private boolean toBoolean(Object state, int index) {
            return (Boolean) invoke(stateToBoolean, state, index);
        }

        private double toNumber(Object state, int index) {
            return (Double) invoke(stateToNumber, state, index);
        }

        private Integer toIntegerOrNull(Object state, int index) {
            return (Integer) invoke(stateToIntegerOrNull, state, index);
        }

        private String toString(Object state, int index) {
            return (String) invoke(stateToString, state, index);
        }

        private int ref(Object state, int index) {
            return (Integer) invoke(stateRef, state, index);
        }

        private void unref(Object state, int ref) {
            invoke(stateUnref, state, ref);
        }

        private void getRef(Object state, int ref) {
            invoke(stateGetRef, state, ref);
        }

        private Object wrapFunction(ToIntFunction<Object> function) {
            ToIntFunction<Object> bridge = function;
            return invokeStatic(luaFuncWrap, null, bridge, "moud");
        }

        private Object readValue(Object state, int index, Class<?> targetType, boolean varArgComponent) {
            if (targetType == Object.class) {
                return readDynamic(state, index);
            }
            if (targetType == String.class) {
                return isNoneOrNil(state, index) ? null : toString(state, index);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                if (!isBoolean(state, index)) {
                    return INVALID;
                }
                return toBoolean(state, index);
            }
            if (targetType == double.class || targetType == Double.class) {
                if (!isNumber(state, index)) {
                    return INVALID;
                }
                return toNumber(state, index);
            }
            if (targetType == float.class || targetType == Float.class) {
                if (!isNumber(state, index)) {
                    return INVALID;
                }
                return (float) toNumber(state, index);
            }
            if (targetType == int.class || targetType == Integer.class) {
                if (!isNumber(state, index)) {
                    return INVALID;
                }
                Integer integer = toIntegerOrNull(state, index);
                return integer == null ? INVALID : integer;
            }
            if (targetType == long.class || targetType == Long.class) {
                if (!isNumber(state, index)) {
                    return INVALID;
                }
                Integer integer = toIntegerOrNull(state, index);
                return integer == null ? (long) toNumber(state, index) : integer.longValue();
            }
            if (targetType.isArray()) {
                if (!isTable(state, index)) {
                    return INVALID;
                }
                Class<?> componentType = targetType.getComponentType();
                int size = len(state, index);
                Object array = Array.newInstance(componentType, size);
                for (int i = 0; i < size; i++) {
                    rawGetI(state, index, i + 1);
                    try {
                        Object value = readValue(state, -1, componentType, true);
                        if (value == INVALID) {
                            return INVALID;
                        }
                        Array.set(array, i, value);
                    } finally {
                        pop(state, 1);
                    }
                }
                return array;
            }
            if (ScriptCallable.class.isAssignableFrom(targetType) || (targetType == Object.class && isFunction(state, index))) {
                if (!isFunction(state, index)) {
                    return INVALID;
                }
                int ref = ref(state, index);
                return new LuauCallableRef(this, state, ref);
            }
            if (targetType == Object.class && varArgComponent) {
                return readDynamic(state, index);
            }
            return INVALID;
        }

        private Object readDynamic(Object state, int index) {
            if (isNoneOrNil(state, index)) {
                return null;
            }
            if (isBoolean(state, index)) {
                return toBoolean(state, index);
            }
            if (isNumber(state, index)) {
                return toNumber(state, index);
            }
            if (isString(state, index)) {
                return toString(state, index);
            }
            if (isFunction(state, index)) {
                int ref = ref(state, index);
                return new LuauCallableRef(this, state, ref);
            }
            if (isTable(state, index)) {
                int size = len(state, index);
                Object[] values = new Object[size];
                for (int i = 0; i < size; i++) {
                    rawGetI(state, index, i + 1);
                    try {
                        values[i] = readDynamic(state, -1);
                    } finally {
                        pop(state, 1);
                    }
                }
                return values;
            }
            return null;
        }

        private static Object invokeStatic(Method method, Object target, Object... args) {
            return invoke(method, target, args);
        }

        private static Object invoke(Method method, Object target, Object... args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IllegalStateException(cause.getMessage(), cause);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        private static Object getField(Field field, Object target) {
            try {
                return field.get(target);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }
}
