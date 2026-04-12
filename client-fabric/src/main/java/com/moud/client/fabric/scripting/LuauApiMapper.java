package com.moud.client.fabric.scripting;

import com.moud.client.fabric.util.ClientDebugLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

final class LuauApiMapper {

    private final LuauRuntime runtime;
    private final List<Object> functionHandles = new ArrayList<>();
    private final ConcurrentHashMap<Class<?>, List<ApiMethod>> methodCache = new ConcurrentHashMap<>();

    LuauApiMapper(LuauRuntime runtime) {
        this.runtime = runtime;
    }

    void pushApiObject(Object thread, Object javaObject) {
        if (javaObject == null) {
            runtime.pushNil(thread);
            return;
        }
        List<ApiMethod> methods = methodCache.computeIfAbsent(javaObject.getClass(), this::buildApiMethods);
        runtime.newTable(thread);
        for (ApiMethod m : methods) {
            Object luaFunc = keepAlive(runtime.wrapFunction(t -> invokeApiMethod(t, javaObject, m)));
            runtime.pushFunction(thread, luaFunc);
            runtime.setField(thread, -2, m.luaName());
        }
    }

    void setApiGlobal(Object thread, String globalName, Object javaObject) {
        pushApiObject(thread, javaObject);
        runtime.setGlobal(thread, globalName);
    }

    Object keepAlive(Object luaFunc) {
        functionHandles.add(luaFunc);
        return luaFunc;
    }

    private int invokeApiMethod(Object thread, Object target, ApiMethod m) {
        try {
            Class<?>[] paramTypes = m.method().getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = readArg(thread, i + 2, paramTypes[i]);
            }
            Object result = m.method().invoke(target, args);
            return pushReturn(thread, m.method().getReturnType(), result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            ClientDebugLog.error("LuauApiMapper", "Error in " + m.luaName() + ": " + cause.getMessage(), cause);
            return 0;
        } catch (Exception e) {
            ClientDebugLog.error("LuauApiMapper", "Error in " + m.luaName() + ": " + e.getMessage(), e);
            return 0;
        }
    }

    private Object readArg(Object thread, int idx, Class<?> type) {
        if (type == String.class)                            { return runtime.isNoneOrNil(thread, idx) ? null : runtime.toStringValue(thread, idx); }
        if (type == boolean.class || type == Boolean.class) { return runtime.isBoolean(thread, idx) && runtime.toBoolean(thread, idx); }
        if (type == float.class   || type == Float.class)   { return runtime.isNumber(thread, idx) ? (float) runtime.toNumber(thread, idx) : 0f; }
        if (type == double.class  || type == Double.class)  { return runtime.isNumber(thread, idx) ? runtime.toNumber(thread, idx) : 0.0; }
        if (type == int.class     || type == Integer.class) { return runtime.isNumber(thread, idx) ? (int)  runtime.toNumber(thread, idx) : 0; }
        if (type == long.class    || type == Long.class)    { return runtime.isNumber(thread, idx) ? (long) runtime.toNumber(thread, idx) : 0L; }
        if (type == Object.class) {
            if (runtime.isBoolean(thread, idx)) return runtime.toBoolean(thread, idx);
            if (runtime.isNumber(thread, idx))  return runtime.toNumber(thread, idx);
            if (runtime.isString(thread, idx))  return runtime.toStringValue(thread, idx);
        }
        return null;
    }

    private int pushReturn(Object thread, Class<?> returnType, Object result) {
        if (returnType == void.class || returnType == Void.class) return 0;
        if (result == null)              { runtime.pushNil(thread);                       return 1; }
        if (result instanceof Boolean b) { runtime.pushBoolean(thread, b);                return 1; }
        if (result instanceof Number  n) { runtime.pushNumber(thread, n.doubleValue());   return 1; }
        if (result instanceof String  s) { runtime.pushString(thread, s);                 return 1; }
        if (result instanceof float[] fa){ for (float v : fa) runtime.pushNumber(thread, v); return fa.length; }
        if (result instanceof double[] da){ for (double v : da) runtime.pushNumber(thread, v); return da.length; }
        runtime.pushNil(thread);
        return 1;
    }

    private List<ApiMethod> buildApiMethods(Class<?> cls) {
        Map<String, ApiMethod> byName = new HashMap<>();
        for (Method m : cls.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            m.setAccessible(true);
            byName.putIfAbsent(m.getName(), new ApiMethod(m.getName(), m));
        }
        return List.copyOf(byName.values());
    }

    private record ApiMethod(String luaName, Method method) {}
}
