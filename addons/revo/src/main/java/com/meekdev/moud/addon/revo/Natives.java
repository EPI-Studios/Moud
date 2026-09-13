package com.meekdev.moud.addon.revo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.OptionalLong;

final class Natives {

    static final int NUMBER = 0;
    static final int STRING = 8;
    static final int ATOM = 9;
    static final int FUNCTION = 10;
    static final int TABLE = 11;

    static final long LAST_NIL_ATOM = 5;
    static final long FALSE_ATOM = 6;
    static final long TRUE_ATOM = 7;

    private static final long BOX = 0x7FF8_0000_0000_0000L;
    private static final long BOX_MASK = 0xFFF8_0000_0000_0000L;
    private static final long PAYLOAD = 0x0000_FFFF_FFFF_FFFFL;
    private static final int ERROR_LIMIT = 1 << 16;
    private static final String LIBRARY = "/natives/linux-x86_64/libmoudrevo.so";

    static final long NIL = boxed(ATOM, 0);
    static final long FALSE = boxed(ATOM, FALSE_ATOM);
    static final long TRUE = boxed(ATOM, TRUE_ATOM);

    static final FunctionDescriptor HOST_FUNCTION = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final Linker LINKER = Linker.nativeLinker();
    private static SymbolLookup library;

    private static MethodHandle vmCreate;
    private static MethodHandle vmDestroy;
    private static MethodHandle lastError;
    private static MethodHandle eval;
    private static MethodHandle intern;
    private static MethodHandle getGlobal;
    private static MethodHandle setGlobal;
    private static MethodHandle tableCreate;
    private static MethodHandle tableLength;
    private static MethodHandle arrayLength;
    private static MethodHandle tableGet;
    private static MethodHandle tableSet;
    private static MethodHandle tableGetIndex;
    private static MethodHandle tablePush;
    private static MethodHandle tableSetName;
    private static MethodHandle tableGetName;
    private static MethodHandle tableRemove;
    private static MethodHandle call;
    private static MethodHandle stringData;
    private static MethodHandle stringLength;
    private static MethodHandle error;
    private static MethodHandle isOk;
    private static MethodHandle okValue;
    private static MethodHandle isError;
    private static MethodHandle function;

    private interface Body<T> {
        T run(Arena scratch) throws Throwable;
    }

    private Natives() {}

    static synchronized void load() {
        if (library != null) return;
        library = SymbolLookup.libraryLookup(extract(), Arena.global());
        ValueLayout.OfLong data = ValueLayout.JAVA_LONG;
        AddressLayout pointer = ValueLayout.ADDRESS;
        ValueLayout.OfBoolean flag = ValueLayout.JAVA_BOOLEAN;
        vmCreate = handle("erevo_vm_create", FunctionDescriptor.of(pointer));
        vmDestroy = handle("erevo_vm_destroy", FunctionDescriptor.ofVoid(pointer));
        lastError = handle("erevo_vm_last_error", FunctionDescriptor.of(pointer, pointer));
        eval = handle("erevo_eval", FunctionDescriptor.of(flag, pointer, pointer, pointer, pointer));
        intern = handle("revo_intern", FunctionDescriptor.of(data, pointer, data, data));
        getGlobal = handle("revo_getglobal", FunctionDescriptor.of(data, pointer, data, data));
        setGlobal = handle("revo_setglobal", FunctionDescriptor.ofVoid(pointer, data, data, data));
        tableCreate = handle("revo_table_create", FunctionDescriptor.of(data, pointer));
        tableLength = handle("revo_table_len", FunctionDescriptor.of(data, pointer, data));
        arrayLength = handle("revo_table_alen", FunctionDescriptor.of(data, pointer, data));
        tableGet = handle("revo_table_get", FunctionDescriptor.of(flag, pointer, data, data, pointer));
        tableSet = handle("revo_table_set", FunctionDescriptor.of(flag, pointer, data, data, data));
        tableGetIndex = handle("revo_table_get_idx", FunctionDescriptor.of(flag, pointer, data, data, pointer));
        tablePush = handle("revo_table_push", FunctionDescriptor.of(flag, pointer, data, data));
        tableSetName = handle("revo_table_set_name", FunctionDescriptor.of(flag, pointer, data, data, data, data));
        tableGetName = handle("revo_table_get_name", FunctionDescriptor.of(flag, pointer, data, data, data, pointer));
        tableRemove = handle("revo_table_remove", FunctionDescriptor.of(flag, pointer, data, data));
        call = handle("revo_call", FunctionDescriptor.of(flag, pointer, data, data, pointer, pointer));
        stringData = handle("revo_string_data", FunctionDescriptor.of(pointer, pointer, data));
        stringLength = handle("revo_string_length", FunctionDescriptor.of(data, pointer, data));
        error = handle("revo_err", FunctionDescriptor.of(data, pointer, data));
        isOk = handle("revo_is_ok", FunctionDescriptor.of(flag, pointer, data));
        okValue = handle("revo_ok_value", FunctionDescriptor.of(flag, pointer, data, pointer));
        isError = handle("revo_is_err", FunctionDescriptor.of(flag, pointer, data));
        function = handle("moud_revo_function", FunctionDescriptor.of(data, pointer, pointer, pointer));
    }

    static Linker linker() {
        return LINKER;
    }

    static long boxed(int tag, long id) {
        return BOX | ((long) tag << 48) | (id & PAYLOAD);
    }

    static int tag(long bits) {
        if ((bits & BOX_MASK) != BOX) return NUMBER;
        return (int) ((bits >>> 48) & 0xF);
    }

    static long payload(long bits) {
        return bits & PAYLOAD;
    }

    static long number(double value) {
        return Double.doubleToRawLongBits(value);
    }

    static MemorySegment createVm() {
        return guarded(scratch -> (MemorySegment) vmCreate.invokeExact());
    }

    static void destroyVm(MemorySegment vm) {
        guarded(scratch -> {
            vmDestroy.invokeExact(vm);
            return null;
        });
    }

    static String lastError(MemorySegment vm) {
        return guarded(scratch -> {
            MemorySegment message = (MemorySegment) lastError.invokeExact(vm);
            if (message.address() == 0) return "";
            return message.reinterpret(ERROR_LIMIT).getString(0);
        });
    }

    static OptionalLong eval(MemorySegment vm, String chunk, String source) {
        return guarded(scratch -> {
            MemorySegment out = scratch.allocate(8);
            boolean ok = (boolean) eval.invokeExact(vm, scratch.allocateFrom(chunk), scratch.allocateFrom(source), out);
            return ok ? OptionalLong.of(read(out)) : OptionalLong.empty();
        });
    }

    static long string(MemorySegment vm, String text) {
        return guarded(scratch -> {
            MemorySegment bytes = utf8(scratch, text);
            long id = (long) intern.invokeExact(vm, bytes.address(), bytes.byteSize());
            return boxed(STRING, id);
        });
    }

    static String text(MemorySegment vm, long id) {
        return guarded(scratch -> {
            MemorySegment data = (MemorySegment) stringData.invokeExact(vm, id);
            long length = (long) stringLength.invokeExact(vm, id);
            if (length == 0 || data.address() == 0) return "";
            return new String(data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        });
    }

    static long getGlobal(MemorySegment vm, String name) {
        return guarded(scratch -> {
            MemorySegment bytes = utf8(scratch, name);
            return (long) getGlobal.invokeExact(vm, bytes.address(), bytes.byteSize());
        });
    }

    static void setGlobal(MemorySegment vm, String name, long value) {
        guarded(scratch -> {
            MemorySegment bytes = utf8(scratch, name);
            setGlobal.invokeExact(vm, bytes.address(), bytes.byteSize(), value);
            return null;
        });
    }

    static long tableCreate(MemorySegment vm) {
        return guarded(scratch -> (long) tableCreate.invokeExact(vm));
    }

    static long tableLength(MemorySegment vm, long table) {
        return guarded(scratch -> (long) tableLength.invokeExact(vm, table));
    }

    static long arrayLength(MemorySegment vm, long table) {
        return guarded(scratch -> (long) arrayLength.invokeExact(vm, table));
    }

    static OptionalLong tableGet(MemorySegment vm, long table, long key) {
        return guarded(scratch -> {
            MemorySegment out = scratch.allocate(8);
            boolean found = (boolean) tableGet.invokeExact(vm, table, key, out);
            return found ? OptionalLong.of(read(out)) : OptionalLong.empty();
        });
    }

    static void tableSet(MemorySegment vm, long table, long key, long value) {
        guarded(scratch -> (boolean) tableSet.invokeExact(vm, table, key, value));
    }

    static OptionalLong tableGetIndex(MemorySegment vm, long table, long index) {
        return guarded(scratch -> {
            MemorySegment out = scratch.allocate(8);
            boolean found = (boolean) tableGetIndex.invokeExact(vm, table, index, out);
            return found ? OptionalLong.of(read(out)) : OptionalLong.empty();
        });
    }

    static void tablePush(MemorySegment vm, long table, long value) {
        guarded(scratch -> (boolean) tablePush.invokeExact(vm, table, value));
    }

    static void tableSetName(MemorySegment vm, long table, String name, long value) {
        guarded(scratch -> {
            MemorySegment bytes = utf8(scratch, name);
            return (boolean) tableSetName.invokeExact(vm, table, bytes.address(), bytes.byteSize(), value);
        });
    }

    static OptionalLong tableGetName(MemorySegment vm, long table, String name) {
        return guarded(scratch -> {
            MemorySegment bytes = utf8(scratch, name);
            MemorySegment out = scratch.allocate(8);
            boolean found = (boolean) tableGetName.invokeExact(vm, table, bytes.address(), bytes.byteSize(), out);
            return found ? OptionalLong.of(read(out)) : OptionalLong.empty();
        });
    }

    static void tableRemove(MemorySegment vm, long table, long key) {
        guarded(scratch -> (boolean) tableRemove.invokeExact(vm, table, key));
    }

    static OptionalLong call(MemorySegment vm, long fn, long[] args) {
        return guarded(scratch -> {
            MemorySegment argv = scratch.allocate(8L * Math.max(1, args.length));
            for (int n = 0; n < args.length; n++) argv.setAtIndex(ValueLayout.JAVA_LONG, n, args[n]);
            MemorySegment out = scratch.allocate(8);
            boolean ok = (boolean) call.invokeExact(vm, fn, (long) args.length, argv, out);
            return ok ? OptionalLong.of(read(out)) : OptionalLong.empty();
        });
    }

    static long error(MemorySegment vm, long value) {
        return guarded(scratch -> (long) error.invokeExact(vm, value));
    }

    static boolean isOk(MemorySegment vm, long result) {
        return guarded(scratch -> (boolean) isOk.invokeExact(vm, result));
    }

    static long okValue(MemorySegment vm, long result) {
        return guarded(scratch -> {
            MemorySegment out = scratch.allocate(8);
            boolean found = (boolean) okValue.invokeExact(vm, result, out);
            return found ? read(out) : NIL;
        });
    }

    static boolean isError(MemorySegment vm, long result) {
        return guarded(scratch -> (boolean) isError.invokeExact(vm, result));
    }

    static long function(MemorySegment vm, String name, MemorySegment stub) {
        return guarded(scratch -> (long) function.invokeExact(vm, scratch.allocateFrom(name), stub));
    }

    private static <T> T guarded(Body<T> body) {
        try (Arena scratch = Arena.ofConfined()) {
            return body.run(scratch);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static MemorySegment utf8(Arena scratch, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) return scratch.allocate(1).asSlice(0, 0);
        return scratch.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
    }

    private static long read(MemorySegment slot) {
        return slot.get(ValueLayout.JAVA_LONG, 0);
    }

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = library.find(name).orElseThrow(() -> new IllegalStateException("libmoudrevo has no " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static Path extract() {
        try (InputStream in = Natives.class.getResourceAsStream(LIBRARY)) {
            if (in == null) throw new IllegalStateException("the revo addon was built without " + LIBRARY);
            Path file = Files.createTempFile("moudrevo", ".so");
            file.toFile().deleteOnExit();
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
