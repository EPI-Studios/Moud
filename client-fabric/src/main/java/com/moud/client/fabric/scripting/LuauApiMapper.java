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
        if (type == LuauCallback.class) {
            if (!runtime.isFunction(thread, idx)) return null;
            runtime.pushValue(thread, idx);
            int ref = runtime.ref(thread, -10000);
            return new LuauCallback(runtime, thread, ref);
        }
        if (Map.class.isAssignableFrom(type)) {
            return runtime.isTable(thread, idx) ? readTable(thread, idx) : null;
        }
        if (type == Object.class) {
            if (runtime.isBoolean(thread, idx)) return runtime.toBoolean(thread, idx);
            if (runtime.isNumber(thread, idx))  return runtime.toNumber(thread, idx);
            if (runtime.isString(thread, idx))  return runtime.toStringValue(thread, idx);
            if (runtime.isTable(thread, idx))   return readTable(thread, idx);
        }
        return null;
    }

    private Map<String, Object> readTable(Object thread, int idx) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        int absIdx = idx > 0 ? idx : runtime.top(thread) + idx + 1;
        runtime.pushNil(thread);
        while (runtime.next(thread, absIdx)) {
            int keyIdx = runtime.top(thread) - 1;
            int valIdx = runtime.top(thread);
            String key;
            if (runtime.isString(thread, keyIdx))      key = runtime.toStringValue(thread, keyIdx);
            else if (runtime.isNumber(thread, keyIdx)) key = Double.toString(runtime.toNumber(thread, keyIdx));
            else                                       key = "";
            Object value;
            if (runtime.isBoolean(thread, valIdx))      value = runtime.toBoolean(thread, valIdx);
            else if (runtime.isNumber(thread, valIdx))  value = runtime.toNumber(thread, valIdx);
            else if (runtime.isString(thread, valIdx))  value = runtime.toStringValue(thread, valIdx);
            else if (runtime.isTable(thread, valIdx))   value = readTable(thread, valIdx);
            else                                        value = null;
            result.put(key, value);
            runtime.top(thread, keyIdx);
        }
        return result;
    }

    private int pushReturn(Object thread, Class<?> returnType, Object result) {
        if (returnType == void.class || returnType == Void.class) return 0;
        if (result == null)              { runtime.pushNil(thread);                       return 1; }
        if (result instanceof Boolean b) { runtime.pushBoolean(thread, b);                return 1; }
        if (result instanceof Number  n) { runtime.pushNumber(thread, n.doubleValue());   return 1; }
        if (result instanceof String  s) { runtime.pushString(thread, s);                 return 1; }
        if (result instanceof float[] fa){ for (float v : fa) runtime.pushNumber(thread, v); return fa.length; }
        if (result instanceof double[] da){ for (double v : da) runtime.pushNumber(thread, v); return da.length; }
        if (result instanceof Map<?, ?> m) {
            runtime.newTable(thread);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pushReturn(thread, e.getValue() == null ? Object.class : e.getValue().getClass(), e.getValue());
                runtime.setField(thread, -2, String.valueOf(e.getKey()));
            }
            return 1;
        }
        pushApiObject(thread, result);
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
