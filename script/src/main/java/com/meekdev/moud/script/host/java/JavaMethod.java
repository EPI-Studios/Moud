package com.meekdev.moud.script.host.java;

import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Builtin;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Invocable;
import com.meekdev.moud.script.mixin.Dispatch;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bytebuddy.jar.asm.Type;

public record JavaMethod(Class<?> type, String name, List<String> overload) implements Invocable {

    private static final Map<String, Builtin> MEMBERS = Map.of(
            "before", new Builtin("JavaMethod:before", a -> Mixins.method(a, Dispatch.Kind.BEFORE)),
            "after", new Builtin("JavaMethod:after", a -> Mixins.method(a, Dispatch.Kind.AFTER)),
            "replace", new Builtin("JavaMethod:replace", a -> Mixins.method(a, Dispatch.Kind.REPLACE)),
            "args", new Builtin("JavaMethod:args", a -> Mixins.method(a, Dispatch.Kind.ARGS)),
            "redirect", new Builtin("JavaMethod:redirect", Mixins::redirect),
            "constant", new Builtin("JavaMethod:constant", Mixins::constant),
            "variable", new Builtin("JavaMethod:variable", Mixins::variable),
            "overload", new Builtin("JavaMethod:overload", JavaMethod::narrow));

    @Override
    public String typeName() {
        return "JavaMethod";
    }

    @Override
    public Object get(String key) {
        if (key.equals("name")) return name;
        if (key.equals("class")) return new JavaLibrary.JavaClass(type);
        Builtin member = MEMBERS.get(key);
        if (member == null) throw new HostError("JavaMethod has no member '%s'", key);
        return member;
    }

    @Override
    public Object invoke(Args a) {
        Object[] given = a.from(0);
        List<Method> candidates = all();
        if (given.length > 0 && given[0] instanceof JavaLibrary.JavaClass c && c.type() == type) {
            Object[] rest = a.from(1);
            return JavaLibrary.invoke(type, name, null, rest, candidates.stream().filter(m -> Modifier.isStatic(m.getModifiers())).toList());
        }
        List<Method> statics = candidates.stream().filter(m -> Modifier.isStatic(m.getModifiers())).toList();
        if (given.length > 0 && given[0] instanceof JavaLibrary.JavaObject self && type.isInstance(self.value())) {
            List<Method> instance = candidates.stream().filter(m -> !Modifier.isStatic(m.getModifiers())).toList();
            if (!instance.isEmpty()) return JavaLibrary.invoke(type, name, self.value(), a.from(1), instance);
        }
        return JavaLibrary.invoke(type, name, null, given, statics);
    }

    public List<Method> all() {
        List<Method> out = new ArrayList<>();
        for (Method method : JavaLibrary.methods(type, name, false)) if (matches(method)) out.add(method);
        for (Method method : JavaLibrary.methods(type, name, true)) if (matches(method)) out.add(method);
        return out;
    }

    public List<Method> declared() {
        List<Method> out = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.isBridge() || method.isSynthetic() || !matches(method)) continue;
            out.add(method);
        }
        if (!out.isEmpty()) return out;
        List<String> near = new ArrayList<>();
        for (Class<?> c = type.getSuperclass(); c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && !near.contains(c.getName())) near.add(c.getName());
            }
        }
        throw new HostError(near.isEmpty()
                ? String.format("%s declares no method '%s'%s", type.getName(), name, overload.isEmpty() ? "" : " matching " + overload)
                : String.format("%s declares no method '%s', it inherits it: hook %s instead", type.getName(), name, String.join(" or ", near)));
    }

    private boolean matches(Method method) {
        if (overload.isEmpty()) return true;
        if (overload.size() == 1) {
            String only = overload.getFirst();
            if (only.startsWith("(")) return Type.getMethodDescriptor(method).startsWith(only);
            if (only.chars().allMatch(Character::isDigit)) return method.getParameterCount() == Integer.parseInt(only);
        }
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != overload.size()) return false;
        for (int n = 0; n < parameters.length; n++) {
            String wanted = overload.get(n);
            if (!wanted.equals(parameters[n].getName()) && !wanted.equals(parameters[n].getSimpleName())) return false;
        }
        return true;
    }

    private static Object narrow(Args a) {
        JavaMethod self = a.self(JavaMethod.class);
        List<String> shape = new ArrayList<>();
        for (Object part : a.from(1)) {
            shape.add(part instanceof Number n ? String.valueOf(n.intValue()) : part instanceof JavaLibrary.JavaClass c ? c.type().getName() : String.valueOf(part));
        }
        JavaMethod narrowed = new JavaMethod(self.type, self.name, List.copyOf(shape));
        if (narrowed.all().isEmpty()) throw new HostError("%s.%s has no overload %s", self.type.getSimpleName(), self.name, shape);
        return narrowed;
    }
}
