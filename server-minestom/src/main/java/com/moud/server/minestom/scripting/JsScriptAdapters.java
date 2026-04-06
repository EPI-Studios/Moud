package com.moud.server.minestom.scripting;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

final class JsScriptAdapters {
    private JsScriptAdapters() {
    }

    static ScriptObject object(Value value) {
        return new JsScriptObject(value);
    }

    static ScriptCallable callable(Value value) {
        return new JsScriptCallable(value);
    }

    private static final class JsScriptObject implements ScriptObject {
        private final Value value;

        private JsScriptObject(Value value) {
            this.value = value;
        }

        @Override
        public boolean hasMethod(String member) {
            if (value == null || member == null) {
                return false;
            }
            try {
                Value fn = value.getMember(member);
                return fn != null && !fn.isNull() && fn.canExecute();
            } catch (Exception ignored) {
                return false;
            }
        }

        @Override
        public void invokeMethod(String member, Object... args) throws ScriptInvocationException {
            if (!hasMethod(member)) {
                return;
            }
            try {
                value.invokeMember(member, args);
            } catch (PolyglotException e) {
                throw new ScriptInvocationException(e.getMessage(), e);
            } catch (Exception e) {
                throw new ScriptInvocationException(e.getMessage(), e);
            }
        }
    }

    private static final class JsScriptCallable implements ScriptCallable {
        private final Value value;

        private JsScriptCallable(Value value) {
            this.value = value;
        }

        @Override
        public void invoke() throws ScriptInvocationException {
            if (value == null || !value.canExecute()) {
                return;
            }
            try {
                value.execute();
            } catch (PolyglotException e) {
                throw new ScriptInvocationException(e.getMessage(), e);
            } catch (Exception e) {
                throw new ScriptInvocationException(e.getMessage(), e);
            }
        }
    }
}
