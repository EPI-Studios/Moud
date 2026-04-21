package com.moud.client.fabric.scripting;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.ToIntFunction;

final class LuauRuntime {

    private static final String LUA_STATE_CLASS           = "net.hollowcube.luau.LuaState";
    private static final String LUA_FUNC_CLASS            = "net.hollowcube.luau.LuaFunc";
    private static final String LUA_COMPILER_CLASS        = "net.hollowcube.luau.compiler.LuauCompiler";
    private static final String LUA_COMPILE_EXCEPTION_CLASS = "net.hollowcube.luau.compiler.LuauCompileException";

    private static volatile boolean linkChecked;
    private static volatile String  linkProblem;

    static boolean isLinked()    { return linkProblem() == null; }

    static String linkProblem() {
        if (linkChecked) return linkProblem;
        synchronized (LuauRuntime.class) {
            if (linkChecked) return linkProblem;
            try {
                Class.forName(LUA_STATE_CLASS);
                Class.forName(LUA_FUNC_CLASS);
                Class.forName(LUA_COMPILER_CLASS);
                Class.forName(LUA_COMPILE_EXCEPTION_CLASS);
                linkProblem = null;
            } catch (Throwable t) {
                boolean classpathHasLuau = System.getProperty("java.class.path", "").contains("luau-");
                linkProblem = "java=" + Runtime.version().feature()
                        + " classpathHasLuau=" + classpathHasLuau
                        + " cause=" + describeChain(t);
            }
            linkChecked = true;
            return linkProblem;
        }
    }

    private static String describeChain(Throwable t) {
        if (t == null) return "unknown";
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (t != null && depth < 8) {
            if (depth > 0) sb.append(" <- ");
            sb.append(t.getClass().getSimpleName());
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) sb.append(": ").append(msg);
            t = t.getCause();
            depth++;
        }
        return sb.toString();
    }

    static void closeState(Object state) {
        if (state instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Throwable ignored) {}
        }
    }

    private final Method luaStateNewState;
    private final Method stateOpenLibs;
    private final Method stateSandbox;
    private final Method stateNewThread;
    private final Method stateSandboxThread;
    private final Method statePop;
    private final Method stateTopGet;
    private final Method stateTopSet;
    private final Method stateLoad;
    private final Method stateCall;
    private final Method stateRef;
    private final Method stateUnref;
    private final Method stateGetRef;
    private final Method stateGetField;
    private final Method stateSetField;
    private final Method stateNewTable;
    private final Method statePushFunction;
    private final Method statePushNil;
    private final Method statePushBoolean;
    private final Method statePushNumber;
    private final Method statePushString;
    private final Method stateIsTable;
    private final Method stateIsFunction;
    private final Method stateIsBoolean;
    private final Method stateIsNumber;
    private final Method stateIsString;
    private final Method stateIsNil;
    private final Method stateIsNoneOrNil;
    private final Method stateToBoolean;
    private final Method stateToNumber;
    private final Method stateToString;
    private final Method stateNext;
    private final Method stateRawlen;
    private final Method statePushValue;
    private final Method luaFuncWrap;
    private final Field  compilerDefault;
    private final Method compilerCompileString;

    LuauRuntime() {
        try {
            Class<?> stateClass    = Class.forName(LUA_STATE_CLASS);
            Class<?> funcClass     = Class.forName(LUA_FUNC_CLASS);
            Class<?> compilerClass = Class.forName(LUA_COMPILER_CLASS);
            Class<?> builtinLibArr = Class.forName("net.hollowcube.luau.BuilinLibrary").arrayType();

            luaStateNewState      = stateClass.getMethod("newState");
            stateOpenLibs         = stateClass.getMethod("openLibs", builtinLibArr);
            stateSandbox          = stateClass.getMethod("sandbox");
            stateNewThread        = stateClass.getMethod("newThread");
            stateSandboxThread    = stateClass.getMethod("sandboxThread");
            statePop              = stateClass.getMethod("pop", int.class);
            stateTopGet           = stateClass.getMethod("top");
            stateTopSet           = stateClass.getMethod("top", int.class);
            stateLoad             = stateClass.getMethod("load", String.class, byte[].class);
            stateCall             = stateClass.getMethod("call", int.class, int.class);
            stateRef              = stateClass.getMethod("ref", int.class);
            stateUnref            = stateClass.getMethod("unref", int.class);
            stateGetRef           = stateClass.getMethod("getRef", int.class);
            stateGetField         = stateClass.getMethod("getField", int.class, String.class);
            stateSetField         = stateClass.getMethod("setField", int.class, String.class);
            stateNewTable         = stateClass.getMethod("newTable");
            statePushFunction     = stateClass.getMethod("pushFunction", funcClass);
            statePushNil          = stateClass.getMethod("pushNil");
            statePushBoolean      = stateClass.getMethod("pushBoolean", boolean.class);
            statePushNumber       = stateClass.getMethod("pushNumber", double.class);
            statePushString       = stateClass.getMethod("pushString", String.class);
            stateIsTable          = stateClass.getMethod("isTable", int.class);
            stateIsFunction       = stateClass.getMethod("isFunction", int.class);
            stateIsBoolean        = stateClass.getMethod("isBoolean", int.class);
            stateIsNumber         = stateClass.getMethod("isNumber", int.class);
            stateIsString         = stateClass.getMethod("isString", int.class);
            stateIsNil            = stateClass.getMethod("isNil", int.class);
            stateIsNoneOrNil      = stateClass.getMethod("isNoneOrNil", int.class);
            stateToBoolean        = stateClass.getMethod("toBoolean", int.class);
            stateToNumber         = stateClass.getMethod("toNumber", int.class);
            stateToString         = stateClass.getMethod("toString", int.class);
            stateNext             = findOptionalMethod(stateClass, "next", int.class);
            stateRawlen           = findOptionalMethod(stateClass, "rawlen", int.class);
            statePushValue        = findOptionalMethod(stateClass, "pushValue", int.class);
            luaFuncWrap           = funcClass.getMethod("wrap", ToIntFunction.class, String.class);
            compilerDefault       = compilerClass.getField("DEFAULT");
            compilerCompileString = compilerClass.getMethod("compile", String.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Luau reflection bridge", e);
        }
    }

    Object newState() { return invokeStatic(luaStateNewState); }

    void openLibs(Object state) {
        try {
            Object empty = java.lang.reflect.Array.newInstance(
                    stateOpenLibs.getParameterTypes()[0].componentType(), 0);
            stateOpenLibs.invoke(state, empty);
        } catch (Exception e) { throw wrap(e); }
    }

    void   sandbox(Object state)       { invoke(stateSandbox, state); }
    Object newThread(Object state)     { return invoke(stateNewThread, state); }
    void   sandboxThread(Object state) { invoke(stateSandboxThread, state); }

    void pop(Object state, int n) { invoke(statePop, state, n); }
    int  top(Object state)        { return (Integer) invoke(stateTopGet, state); }
    void top(Object state, int n) { invoke(stateTopSet, state, n); }

    void load(Object state, String chunk, byte[] data) { invoke(stateLoad, state, chunk, data); }
    void call(Object state, int nargs, int nret)       { invoke(stateCall, state, nargs, nret); }

    int  ref(Object state, int idx)    { return (Integer) invoke(stateRef, state, idx); }
    void unref(Object state, int ref)  { invoke(stateUnref, state, ref); }
    void getRef(Object state, int ref) { invoke(stateGetRef, state, ref); }

    void getField(Object state, int idx, String key) { invoke(stateGetField, state, idx, key); }
    void setField(Object state, int idx, String key) { invoke(stateSetField, state, idx, key); }
    void setGlobal(Object state, String name)        { setField(state, -10002, name); }
    void newTable(Object state)                      { invoke(stateNewTable, state); }

    void pushFunction(Object state, Object f) { invoke(statePushFunction, state, f); }
    void pushNil(Object state)                { invoke(statePushNil, state); }
    void pushBoolean(Object state, boolean v) { invoke(statePushBoolean, state, v); }
    void pushNumber(Object state, double v)   { invoke(statePushNumber, state, v); }
    void pushString(Object state, String v)   { invoke(statePushString, state, v); }

    boolean isTable(Object state, int i)     { return (Boolean) invoke(stateIsTable, state, i); }
    boolean isFunction(Object state, int i)  { return (Boolean) invoke(stateIsFunction, state, i); }
    boolean isBoolean(Object state, int i)   { return (Boolean) invoke(stateIsBoolean, state, i); }
    boolean isNumber(Object state, int i)    { return (Boolean) invoke(stateIsNumber, state, i); }
    boolean isString(Object state, int i)    { return (Boolean) invoke(stateIsString, state, i); }
    boolean isNil(Object state, int i)       { return (Boolean) invoke(stateIsNil, state, i); }
    boolean isNoneOrNil(Object state, int i) { return (Boolean) invoke(stateIsNoneOrNil, state, i); }

    boolean toBoolean(Object state, int i)     { return (Boolean) invoke(stateToBoolean, state, i); }
    double  toNumber(Object state, int i)      { return (Double)  invoke(stateToNumber, state, i); }
    String  toStringValue(Object state, int i) { return (String)  invoke(stateToString, state, i); }

    boolean next(Object state, int idx) {
        if (stateNext == null) return false;
        Object r = invoke(stateNext, state, idx);
        if (r instanceof Boolean b) return b;
        if (r instanceof Integer i) return i != 0;
        return false;
    }

    int rawlen(Object state, int idx) {
        if (stateRawlen == null) return 0;
        Object r = invoke(stateRawlen, state, idx);
        return r instanceof Integer i ? i : 0;
    }

    void pushValue(Object state, int idx) {
        if (statePushValue == null) return;
        invoke(statePushValue, state, idx);
    }

    private static Method findOptionalMethod(Class<?> cls, String name, Class<?>... params) {
        try { return cls.getMethod(name, params); } catch (NoSuchMethodException e) { return null; }
    }

    byte[] compile(String code) {
        try {
            Object compiler = compilerDefault.get(null);
            return (byte[]) compilerCompileString.invoke(compiler, code);
        } catch (Exception e) { throw wrap(e); }
    }

    Object wrapFunction(ToIntFunction<Object> fn) {
        try {
            return luaFuncWrap.invoke(null, fn, "moud");
        } catch (Exception e) { throw wrap(e); }
    }

    private Object invoke(Method m, Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage(), cause);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Object invokeStatic(Method m, Object... args) {
        return invoke(m, null, args);
    }

    private RuntimeException wrap(Exception e) {
        if (e instanceof InvocationTargetException ite && ite.getCause() != null) {
            return new RuntimeException(ite.getCause().getMessage(), ite.getCause());
        }
        return new RuntimeException(e.getMessage(), e);
    }
}
