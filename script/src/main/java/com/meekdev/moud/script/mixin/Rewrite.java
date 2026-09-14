package com.meekdev.moud.script.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

public sealed interface Rewrite {

    String key();

    int ordinal();

    record Redirect(String owner, String name, String descriptor, boolean statics, int ordinal) implements Rewrite {
        public String key() {
            return "redirect " + owner + "." + name + descriptor + " #" + ordinal;
        }
    }

    record Constant(Object value, int ordinal) implements Rewrite {
        public String key() {
            return "constant " + value.getClass().getSimpleName() + " " + value + " #" + ordinal;
        }
    }

    record Variable(int slot, String descriptor, int ordinal) implements Rewrite {
        public String key() {
            return "variable " + slot + " " + descriptor + " #" + ordinal;
        }
    }

    final class Visitor extends MethodVisitor {

        private static final String DISPATCH = "com/meekdev/moud/script/mixin/Dispatch";
        private static final String OBJECT = "java/lang/Object";

        private final Rewrite rewrite;
        private final String site;
        private final AtomicInteger matched;
        private int seen;

        Visitor(MethodVisitor next, Rewrite rewrite, String site, AtomicInteger matched) {
            super(Opcodes.ASM9, next);
            this.rewrite = rewrite;
            this.site = site;
            this.matched = matched;
        }

        private boolean take() {
            seen++;
            if (rewrite.ordinal() > 0 && seen != rewrite.ordinal()) return false;
            matched.incrementAndGet();
            return true;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (rewrite instanceof Redirect redirect && owner.equals(redirect.owner()) && name.equals(redirect.name())
                    && descriptor.equals(redirect.descriptor()) && (opcode == Opcodes.INVOKESTATIC) == redirect.statics() && take()) {
                Type[] parameters = Type.getArgumentTypes(descriptor);
                pack(parameters);
                if (redirect.statics()) {
                    super.visitInsn(Opcodes.ACONST_NULL);
                    super.visitInsn(Opcodes.SWAP);
                }
                super.visitLdcInsn(site);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, DISPATCH, "redirect",
                        "(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                unbox(Type.getReturnType(descriptor));
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
            if (!(rewrite instanceof Constant constant)) return;
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) constant(Type.INT_TYPE, opcode - Opcodes.ICONST_0, constant);
            else if (opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1) constant(Type.LONG_TYPE, opcode - Opcodes.LCONST_0, constant);
            else if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2) constant(Type.FLOAT_TYPE, opcode - Opcodes.FCONST_0, constant);
            else if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1) constant(Type.DOUBLE_TYPE, opcode - Opcodes.DCONST_0, constant);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            super.visitIntInsn(opcode, operand);
            if (rewrite instanceof Constant constant && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
                constant(Type.INT_TYPE, operand, constant);
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            if (!(rewrite instanceof Constant constant)) return;
            switch (value) {
                case Integer i -> constant(Type.INT_TYPE, i, constant);
                case Long l -> constant(Type.LONG_TYPE, l, constant);
                case Float f -> constant(Type.FLOAT_TYPE, f, constant);
                case Double d -> constant(Type.DOUBLE_TYPE, d, constant);
                case String s -> {
                    if (s.equals(constant.value()) && take()) replace(Type.getType(String.class), "constant");
                }
                default -> {}
            }
        }

        private void constant(Type type, Number found, Constant constant) {
            if (!(constant.value() instanceof Number wanted)) return;
            boolean same = switch (type.getSort()) {
                case Type.FLOAT -> found.floatValue() == wanted.floatValue();
                case Type.DOUBLE -> found.doubleValue() == wanted.doubleValue();
                default -> found.doubleValue() == wanted.doubleValue();
            };
            if (same && take()) replace(type, "constant");
        }

        @Override
        public void visitVarInsn(int opcode, int slot) {
            if (rewrite instanceof Variable variable && slot == variable.slot() && opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE && take()) {
                Type type = variable.descriptor() != null ? Type.getType(variable.descriptor()) : switch (opcode) {
                    case Opcodes.LSTORE -> Type.LONG_TYPE;
                    case Opcodes.FSTORE -> Type.FLOAT_TYPE;
                    case Opcodes.DSTORE -> Type.DOUBLE_TYPE;
                    case Opcodes.ASTORE -> Type.getObjectType(OBJECT);
                    default -> Type.INT_TYPE;
                };
                replace(type, "variable");
            }
            super.visitVarInsn(opcode, slot);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack + 6, maxLocals);
        }

        private void replace(Type type, String entry) {
            box(type);
            super.visitLdcInsn(site);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, DISPATCH, entry, "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
            unbox(type);
        }

        private void pack(Type[] parameters) {
            super.visitLdcInsn(parameters.length);
            super.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT);
            for (int n = parameters.length - 1; n >= 0; n--) {
                if (parameters[n].getSize() == 2) {
                    super.visitInsn(Opcodes.DUP_X2);
                    super.visitInsn(Opcodes.POP);
                } else {
                    super.visitInsn(Opcodes.SWAP);
                }
                box(parameters[n]);
                super.visitInsn(Opcodes.SWAP);
                super.visitInsn(Opcodes.DUP_X1);
                super.visitInsn(Opcodes.SWAP);
                super.visitLdcInsn(n);
                super.visitInsn(Opcodes.SWAP);
                super.visitInsn(Opcodes.AASTORE);
            }
        }

        private void box(Type type) {
            String boxed = switch (type.getSort()) {
                case Type.BOOLEAN -> "java/lang/Boolean";
                case Type.CHAR -> "java/lang/Character";
                case Type.BYTE -> "java/lang/Byte";
                case Type.SHORT -> "java/lang/Short";
                case Type.INT -> "java/lang/Integer";
                case Type.FLOAT -> "java/lang/Float";
                case Type.LONG -> "java/lang/Long";
                case Type.DOUBLE -> "java/lang/Double";
                default -> null;
            };
            if (boxed != null) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, boxed, "valueOf", "(" + type.getDescriptor() + ")L" + boxed + ";", false);
            }
        }

        private void unbox(Type type) {
            switch (type.getSort()) {
                case Type.VOID -> super.visitInsn(Opcodes.POP);
                case Type.BOOLEAN -> {
                    super.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                }
                case Type.CHAR -> {
                    super.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                }
                case Type.BYTE, Type.SHORT, Type.INT, Type.FLOAT, Type.LONG, Type.DOUBLE -> {
                    super.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    String name = switch (type.getSort()) {
                        case Type.BYTE -> "byteValue";
                        case Type.SHORT -> "shortValue";
                        case Type.INT -> "intValue";
                        case Type.FLOAT -> "floatValue";
                        case Type.LONG -> "longValue";
                        default -> "doubleValue";
                    };
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", name, "()" + type.getDescriptor(), false);
                }
                case Type.ARRAY -> super.visitTypeInsn(Opcodes.CHECKCAST, type.getDescriptor());
                default -> {
                    if (!type.getInternalName().equals(OBJECT)) super.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
                }
            }
        }
    }
}
