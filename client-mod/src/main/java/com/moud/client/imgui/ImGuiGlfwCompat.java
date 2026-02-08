package com.moud.client.imgui;

import foundry.veil.impl.client.imgui.VeilImGuiImplGlfw;
import imgui.ImVec2;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ImGuiGlfwCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoudImGuiCompat");

    private static final Field FIELD_WINDOW_PTR = findField("windowPtr");
    private static final Field FIELD_MOUSE_WINDOW_PTR = findField("mouseWindowPtr");
    private static final Field FIELD_KEY_OWNER_WINDOWS = findField("keyOwnerWindows");
    private static final Field FIELD_PREV_WINDOW_FOCUS = findField("prevUserCallbackWindowFocus");
    private static final Field FIELD_PREV_CURSOR_POS = findField("prevUserCallbackCursorPos");
    private static final Field FIELD_PREV_CURSOR_ENTER = findField("prevUserCallbackCursorEnter");
    private static final Field FIELD_PREV_MOUSE_BUTTON = findField("prevUserCallbackMouseButton");
    private static final Field FIELD_PREV_SCROLL = findField("prevUserCallbackScroll");
    private static final Field FIELD_PREV_KEY = findField("prevUserCallbackKey");
    private static final Field FIELD_PREV_CHAR = findField("prevUserCallbackChar");
    private static final Field FIELD_PREV_MONITOR = findField("prevUserCallbackMonitor");

    private static final Constructor<?> VEIL_DATA_CTOR = findVeilDataConstructor();

    private static final Map<VeilImGuiImplGlfw, Object> DATA =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ImGuiImplGlfw, State> STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ImGuiGlfwCompat() {
    }

    public static Object getData(VeilImGuiImplGlfw impl) {
        return DATA.computeIfAbsent(impl, ImGuiGlfwCompat::createData);
    }

    public static long getWindow(ImGuiImplGlfw impl) {
        return getLong(FIELD_WINDOW_PTR, impl);
    }

    public static long getMouseWindow(ImGuiImplGlfw impl) {
        return getLong(FIELD_MOUSE_WINDOW_PTR, impl);
    }

    public static void setMouseWindow(ImGuiImplGlfw impl, long value) {
        setLong(FIELD_MOUSE_WINDOW_PTR, impl, value);
    }

    public static ImVec2 getLastValidMousePos(ImGuiImplGlfw impl) {
        return state(impl).lastValidMousePos;
    }

    public static long[] getKeyOwnerWindows(ImGuiImplGlfw impl) {
        long[] array = (long[]) get(FIELD_KEY_OWNER_WINDOWS, impl);
        if (array != null) {
            return array;
        }
        State state = state(impl);
        if (state.keyOwnerWindowsFallback == null) {
            state.keyOwnerWindowsFallback = new long[512];
        }
        return state.keyOwnerWindowsFallback;
    }

    public static GLFWWindowFocusCallback getPrevWindowFocusCallback(ImGuiImplGlfw impl) {
        return (GLFWWindowFocusCallback) get(FIELD_PREV_WINDOW_FOCUS, impl);
    }

    public static GLFWCursorPosCallback getPrevCursorPosCallback(ImGuiImplGlfw impl) {
        return (GLFWCursorPosCallback) get(FIELD_PREV_CURSOR_POS, impl);
    }

    public static GLFWCursorEnterCallback getPrevCursorEnterCallback(ImGuiImplGlfw impl) {
        return (GLFWCursorEnterCallback) get(FIELD_PREV_CURSOR_ENTER, impl);
    }

    public static GLFWMouseButtonCallback getPrevMouseButtonCallback(ImGuiImplGlfw impl) {
        return (GLFWMouseButtonCallback) get(FIELD_PREV_MOUSE_BUTTON, impl);
    }

    public static GLFWScrollCallback getPrevScrollCallback(ImGuiImplGlfw impl) {
        return (GLFWScrollCallback) get(FIELD_PREV_SCROLL, impl);
    }

    public static GLFWKeyCallback getPrevKeyCallback(ImGuiImplGlfw impl) {
        return (GLFWKeyCallback) get(FIELD_PREV_KEY, impl);
    }

    public static GLFWCharCallback getPrevCharCallback(ImGuiImplGlfw impl) {
        return (GLFWCharCallback) get(FIELD_PREV_CHAR, impl);
    }

    public static GLFWMonitorCallback getPrevMonitorCallback(ImGuiImplGlfw impl) {
        return (GLFWMonitorCallback) get(FIELD_PREV_MONITOR, impl);
    }

    public static void clear(VeilImGuiImplGlfw impl) {
        DATA.remove(impl);
        STATE.remove(impl);
    }

    private static Object createData(VeilImGuiImplGlfw impl) {
        if (VEIL_DATA_CTOR == null) {
            LOGGER.warn("ImGuiGlfwCompat: cannot instantiate VeilData - constructor missing");
            return null;
        }
        try {
            Object instance = VEIL_DATA_CTOR.newInstance();
            if (instance instanceof VeilDataAccessor accessor) {
                accessor.moud$setOwner(impl);
            }
            return instance;
        } catch (ReflectiveOperationException ex) {
            LOGGER.error("ImGuiGlfwCompat: failed to instantiate VeilData", ex);
        }
        return null;
    }

    private static State state(ImGuiImplGlfw impl) {
        return STATE.computeIfAbsent(impl, unused -> new State());
    }

    private static Field findField(String name) {
        try {
            Field field = ImGuiImplGlfw.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            LOGGER.debug("ImGuiGlfwCompat: field {} not found on ImGuiImplGlfw", name, ex);
            return null;
        }
    }

    private static Constructor<?> findVeilDataConstructor() {
        try {
            Class<?> clazz = Class.forName("foundry.veil.impl.client.imgui.VeilImGuiImplGlfw$VeilData");
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor;
        } catch (ReflectiveOperationException ex) {
            LOGGER.error("ImGuiGlfwCompat: unable to resolve VeilData constructor", ex);
            return null;
        }
    }

    private static long getLong(Field field, ImGuiImplGlfw impl) {
        if (field == null) {
            return 0L;
        }
        try {
            return field.getLong(impl);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("ImGuiGlfwCompat: failed to read long field {}", field.getName(), ex);
            return 0L;
        }
    }

    private static void setLong(Field field, ImGuiImplGlfw impl, long value) {
        if (field == null) {
            return;
        }
        try {
            field.setLong(impl, value);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("ImGuiGlfwCompat: failed to write long field {}", field.getName(), ex);
        }
    }

    private static Object get(Field field, ImGuiImplGlfw impl) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(impl);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("ImGuiGlfwCompat: failed to read field {}", field.getName(), ex);
            return null;
        }
    }

    private static final class State {
        private final ImVec2 lastValidMousePos = new ImVec2();
        private long[] keyOwnerWindowsFallback;
    }
}