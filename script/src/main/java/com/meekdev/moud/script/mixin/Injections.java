package com.meekdev.moud.script.mixin;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public final class Injections {

    public record Local(String name, String descriptor, int slot) {}

    private static Instrumentation instrumentation;

    private static final Map<String, ResettableClassFileTransformer> ADVICE = new HashMap<>();
    private static final Map<String, ResettableClassFileTransformer> REWRITES = new HashMap<>();

    private Injections() {}

    public static String id(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName()
                + new MethodDescription.ForLoadedMethod(method).getDescriptor();
    }

    public static synchronized void hook(Method method, Dispatch.Hook hook) {
        bodied(method);
        String id = id(method);
        Dispatch.Target target = Dispatch.TARGETS.get(id);
        if (target == null) {
            boolean statics = Modifier.isStatic(method.getModifiers());
            target = new Dispatch.Target(method, original(method, statics), statics);
            Dispatch.TARGETS.put(id, target);
            try {
                Class<?> advice = method.getReturnType() == void.class ? VoidAdvice.class : ValueAdvice.class;
                ADVICE.put(id, install(method.getDeclaringClass(), builder -> builder.visit(Advice.to(advice).on(ElementMatchers.is(method)))));
            } catch (RuntimeException e) {
                Dispatch.TARGETS.remove(id);
                throw e;
            }
        }
        Dispatch.add(target.hooks, hook);
    }

    public static synchronized void unhook(Method method, Dispatch.Hook hook) {
        String id = id(method);
        Dispatch.Target target = Dispatch.TARGETS.get(id);
        if (target == null) return;
        target.hooks.remove(hook);
        if (target.hooks.isEmpty()) {
            Dispatch.TARGETS.remove(id);
            ResettableClassFileTransformer transformer = ADVICE.remove(id);
            if (transformer != null) transformer.reset(instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
    }

    public static synchronized String rewrite(Method method, Rewrite rewrite, Method callee, Dispatch.Hook hook) {
        bodied(method);
        String id = id(method) + " " + rewrite.key();
        Dispatch.Site site = Dispatch.SITES.get(id);
        if (!REWRITES.containsKey(id)) {
            site = switch (rewrite) {
                case Rewrite.Redirect redirect -> new Dispatch.Site(id, callee.getParameterTypes(), callee.getReturnType(),
                        called(callee, method.getDeclaringClass()), !redirect.statics());
                default -> new Dispatch.Site(id, new Class<?>[0], Object.class, null, false);
            };
            Dispatch.SITES.put(id, site);
            AtomicInteger matched = new AtomicInteger();
            String siteId = id;
            AsmVisitorWrapper wrapper = new AsmVisitorWrapper.ForDeclaredMethods().method(ElementMatchers.is(method),
                    (type, described, visitor, context, pool, writerFlags, readerFlags) -> new Rewrite.Visitor(visitor, rewrite, siteId, matched));
            ResettableClassFileTransformer transformer;
            try {
                transformer = install(method.getDeclaringClass(), builder -> builder.visit(wrapper));
            } catch (RuntimeException e) {
                Dispatch.SITES.remove(id);
                throw e;
            }
            if (matched.get() == 0) {
                transformer.reset(instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                Dispatch.SITES.remove(id);
                throw new IllegalArgumentException(method.getDeclaringClass().getSimpleName() + "." + method.getName() + " has no " + describe(rewrite));
            }
            REWRITES.put(id, transformer);
        }
        Dispatch.add(site.hooks, hook);
        return id;
    }

    public static synchronized void unrewrite(String id, Dispatch.Hook hook) {
        Dispatch.Site site = Dispatch.SITES.get(id);
        if (site == null) return;
        site.hooks.remove(hook);
        if (site.hooks.isEmpty()) {
            ResettableClassFileTransformer transformer = REWRITES.remove(id);
            if (transformer != null) transformer.reset(instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
        }
    }

    public static synchronized int installed() {
        return ADVICE.size() + REWRITES.size();
    }

    public static List<Local> locals(Method method) {
        String descriptor = Type.getMethodDescriptor(method);
        List<Local> found = new ArrayList<>();
        byte[] bytes;
        try {
            bytes = ClassFileLocator.ForClassLoader.read(method.getDeclaringClass());
        } catch (RuntimeException e) {
            return found;
        }
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals(method.getName()) || !desc.equals(descriptor)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLocalVariable(String local, String desc, String signature, Label start, Label end, int index) {
                        found.add(new Local(local, desc, index));
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return found;
    }

    private static String describe(Rewrite rewrite) {
        return switch (rewrite) {
            case Rewrite.Redirect r -> "call to " + r.owner().substring(r.owner().lastIndexOf('/') + 1) + "." + r.name()
                    + (r.ordinal() > 0 ? " number " + r.ordinal() : "");
            case Rewrite.Constant c -> "constant " + c.value() + (c.ordinal() > 0 ? " number " + c.ordinal() : "");
            case Rewrite.Variable v -> "store to local slot " + v.slot() + (v.ordinal() > 0 ? " number " + v.ordinal() : "");
        };
    }

    private static void bodied(Method method) {
        if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
            throw new IllegalArgumentException(method + " has no body to put code in: hook the class that implements it");
        }
    }

    private static MethodHandle original(Method method, boolean statics) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
            return statics ? lookup.unreflect(method) : lookup.unreflectSpecial(method, method.getDeclaringClass());
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("can not reach " + method + ": " + e.getMessage());
        }
    }

    private static MethodHandle called(Method callee, Class<?> caller) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(callee.getDeclaringClass(), MethodHandles.lookup());
            return lookup.unreflect(callee);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("can not reach " + callee + " from " + caller.getName() + ": " + e.getMessage());
        }
    }

    private interface Transform {
        DynamicType.Builder<?> apply(DynamicType.Builder<?> builder);
    }

    private static ResettableClassFileTransformer install(Class<?> type, Transform transform) {
        List<Throwable> failures = new ArrayList<>();
        ResettableClassFileTransformer transformer = new AgentBuilder.Default(new ByteBuddy().with(TypeValidation.DISABLED))
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.DECORATE)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .ignore(ElementMatchers.none())
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader loader, JavaModule module, boolean loaded, Throwable throwable) {
                        failures.add(throwable);
                    }
                })
                .type(ElementMatchers.is(type))
                .transform((builder, description, loader, module, domain) -> transform.apply(builder))
                .installOn(instrumentation());
        if (!failures.isEmpty()) {
            transformer.reset(instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
            Throwable first = failures.getFirst();
            throw new IllegalStateException("could not rewrite " + type.getName() + ": " + first, first);
        }
        return transformer;
    }

    public static void prepare() {
        Thread warm = new Thread(() -> {
            try {
                instrumentation();
            } catch (RuntimeException ignored) {
            }
        }, "moud-agent");
        warm.setDaemon(true);
        warm.start();
    }

    private static synchronized Instrumentation instrumentation() {
        if (instrumentation == null) instrumentation = ByteBuddyAgent.install();
        return instrumentation;
    }
}
