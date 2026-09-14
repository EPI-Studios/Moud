package com.meekdev.moud.script.mixin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class ValueAdvice {

    private ValueAdvice() {}

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
            @Advice.AllArguments Object[] args, @Advice.Enter Object entered,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result) {
        if (entered instanceof Dispatch.Cancelled) {
            result = Dispatch.cancelled(entered);
        } else if (entered == null) {
            result = Dispatch.tail(id, self, args, result);
        }
    }
}
