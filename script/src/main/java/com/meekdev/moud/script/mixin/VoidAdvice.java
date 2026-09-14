package com.meekdev.moud.script.mixin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class VoidAdvice {

    private VoidAdvice() {}

    @Advice.OnMethodEnter(skipOn = Dispatch.Cancelled.class)
    public static Object enter(@Advice.Origin("#t.#m#d") String id, @Advice.This(optional = true) Object self,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args) {
        Object[] mine = args;
        Object entered = Dispatch.head(id, self, mine);
        args = mine;
        return entered;
    }

    @Advice.OnMethodExit
    public static void exit(@Advice.Origin("#t.#m#d") String id, @Advice.This(optional = true) Object self,
            @Advice.AllArguments Object[] args, @Advice.Enter Object entered) {
        if (entered == null) Dispatch.tail(id, self, args, null);
    }
}
