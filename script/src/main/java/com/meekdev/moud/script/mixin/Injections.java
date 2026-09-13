package com.meekdev.moud.script.mixin;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatchers;

public final class Injections {

    private static Instrumentation instrumentation;

    private record Installed(ResettableClassFileTransformer transformer, Dispatch.Target target) {}

    private static final Map<String, Installed> INSTALLED = new HashMap<>();

    private Injections() {}

    public static String id(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName()
                + new MethodDescription.ForLoadedMethod(method).getDescriptor();
    }

    public static synchronized void add(Method method, Dispatch.Hook hook, boolean head) {
        if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
            throw new IllegalArgumentException(method + " has no body to put code in: hook the class that implements it");
        }
        String id = id(method);
        Installed installed = INSTALLED.get(id);
        if (installed == null) {
            Dispatch.Target target = new Dispatch.Target(method.getParameterTypes(), method.getReturnType());
            Dispatch.TARGETS.put(id, target);
            installed = new Installed(install(method), target);
            INSTALLED.put(id, installed);
        }
        (head ? installed.target().heads : installed.target().tails).add(hook);
    }

    public static synchronized void remove(Method method, Dispatch.Hook hook) {
        String id = id(method);
        Installed installed = INSTALLED.get(id);
        if (installed == null) return;
        installed.target().heads.remove(hook);
        installed.target().tails.remove(hook);
        if (installed.target().heads.isEmpty() && installed.target().tails.isEmpty()) {
            INSTALLED.remove(id);
            Dispatch.TARGETS.remove(id);
            installed.transformer().reset(instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
    }

    public static synchronized int installed() {
        return INSTALLED.size();
    }

    private static ResettableClassFileTransformer install(Method method) {
        Class<?> advice = method.getReturnType() == void.class ? VoidAdvice.class : ValueAdvice.class;
        Class<?> type = method.getDeclaringClass();
        return new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .ignore(ElementMatchers.none())
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .type(ElementMatchers.is(type))
                .transform((builder, description, loader, module, domain) ->
                        builder.visit(Advice.to(advice).on(ElementMatchers.is(method))))
                .installOn(instrumentation());
    }

    private static Instrumentation instrumentation() {
        if (instrumentation == null) instrumentation = ByteBuddyAgent.install();
        return instrumentation;
    }
}
