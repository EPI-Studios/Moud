package com.moud.client.imgui;

import imgui.ImDrawData;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.internal.ImGuiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ImGuiCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("MoudImGuiCompat");

    private static final Method METHOD_DESTROY_DEVICE_OBJECTS = find("destroyDeviceObjects");
    private static final Method METHOD_DESTROY_FONTS_TEXTURE = find("destroyFontsTexture");
    private static final Method METHOD_CREATE_FONTS_TEXTURE = find("createFontsTexture");
    private static final Method METHOD_UPDATE_FONTS_TEXTURE = find("updateFontsTexture");
    private static final Method METHOD_DISPOSE = find("dispose");
    private static final Method METHOD_INIT_WITH_STRING = find("init", String.class);
    private static final Method METHOD_INIT_NO_ARGS = find("init");
    private static final Method METHOD_NEW_FRAME = find("newFrame");
    private static final Method METHOD_RENDER_DRAW_DATA = find("renderDrawData", ImDrawData.class);
    private static final Field FIELD_DATA = findField(ImGuiImplGl3.class, "data");
    private static final Field FIELD_SHADER_HANDLE_IN_DATA = FIELD_DATA != null
            ? findField(FIELD_DATA.getType(), "shaderHandle")
            : null;
    private static final Field FIELD_SHADER_HANDLE_FLAT = findField(ImGuiImplGl3.class, "gShaderHandle");
    private static final Field FIELD_SHADER_HANDLE_FALLBACK = locateShaderField();

    private ImGuiCompat() {
    }

    private static Method find(String name, Class<?>... params) {
        try {
            Method method = ImGuiImplGl3.class.getMethod(name, params);
            method.setAccessible(true);
            LOGGER.debug("ImGuiCompat resolved method {}", method);
            return method;
        } catch (NoSuchMethodException ignored) {
            LOGGER.debug("ImGuiCompat: method {} not present in this imgui build", name);
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            LOGGER.debug("ImGuiCompat resolved field {}.{}", owner.getSimpleName(), name);
            return field;
        } catch (NoSuchFieldException ignored) {
            LOGGER.debug("ImGuiCompat: field {}.{} not present in this imgui build", owner.getSimpleName(), name);
            return null;
        }
    }

    private static Field locateShaderField() {
        for (Field field : ImGuiImplGl3.class.getDeclaredFields()) {
            if (field.getType() == int.class) {
                String name = field.getName().toLowerCase();
                if (name.contains("shader") && name.contains("handle")) {
                    field.setAccessible(true);
                    LOGGER.debug("ImGuiCompat resolved fallback shader field {}", field.getName());
                    return field;
                }
            }
        }
        LOGGER.debug("ImGuiCompat: fallback shader field not found");
        return null;
    }

    public static void destroyDeviceObjects(ImGuiImplGl3 impl) {
        if (!invokeVoid(impl, METHOD_DESTROY_DEVICE_OBJECTS)) {
            invokeVoid(impl, METHOD_DISPOSE);
        }
    }

    public static void destroyFontsTexture(ImGuiImplGl3 impl) {
        invokeVoid(impl, METHOD_DESTROY_FONTS_TEXTURE);
    }

    public static boolean createFontsTexture(ImGuiImplGl3 impl) {
        if (METHOD_CREATE_FONTS_TEXTURE != null) {
            try {
                Object result = METHOD_CREATE_FONTS_TEXTURE.invoke(impl);
                return !(result instanceof Boolean b) || b;
            } catch (Throwable t) {
                LOGGER.warn("ImGuiCompat: createFontsTexture failed", t);
                return false;
            }
        }
        invokeVoid(impl, METHOD_UPDATE_FONTS_TEXTURE);
        return true;
    }

    public static boolean init(ImGuiImplGl3 impl, String glslVersion) {

        if (METHOD_INIT_WITH_STRING != null) {
            try {
                Object result = METHOD_INIT_WITH_STRING.invoke(impl, glslVersion);
                return !(result instanceof Boolean b) || b;
            } catch (Throwable t) {
                LOGGER.warn("ImGuiCompat: init(String) failed, trying no-args version", t);
            }
        }

        if (METHOD_INIT_NO_ARGS != null) {
            try {
                Object result = METHOD_INIT_NO_ARGS.invoke(impl);
                return !(result instanceof Boolean b) || b;
            } catch (Throwable t) {
                LOGGER.warn("ImGuiCompat: init() failed", t);
                return false;
            }
        }

        LOGGER.error("ImGuiCompat: No compatible init method found!");
        return false;
    }

    public static void newFrame(ImGuiImplGl3 impl) {
        invokeVoid(impl, METHOD_NEW_FRAME);
    }

    public static void renderDrawData(ImGuiImplGl3 impl, ImDrawData drawData) {
        if (METHOD_RENDER_DRAW_DATA != null) {
            try {
                METHOD_RENDER_DRAW_DATA.invoke(impl, drawData);
            } catch (Throwable t) {
                LOGGER.debug("ImGuiCompat: invocation of renderDrawData failed", t);
            }
        }
    }

    public static int getShaderHandle(ImGuiImplGl3 impl) {

        if (FIELD_DATA != null && FIELD_SHADER_HANDLE_IN_DATA != null) {
            try {
                Object data = FIELD_DATA.get(impl);
                if (data != null) {
                    int handle = FIELD_SHADER_HANDLE_IN_DATA.getInt(data);
                    if (handle != 0) {
                        return handle;
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("ImGuiCompat: failed to read shader handle from Data", t);
            }
        }

        if (FIELD_SHADER_HANDLE_FLAT != null) {
            try {
                int handle = FIELD_SHADER_HANDLE_FLAT.getInt(impl);
                if (handle != 0) {
                    return handle;
                }
            } catch (Throwable t) {
                LOGGER.debug("ImGuiCompat: failed to read shader handle from flat field", t);
            }
        }

        if (FIELD_SHADER_HANDLE_FALLBACK != null) {
            try {
                int handle = FIELD_SHADER_HANDLE_FALLBACK.getInt(impl);
                if (handle != 0) {
                    return handle;
                }
            } catch (Throwable t) {
                LOGGER.debug("ImGuiCompat: failed to read shader handle via fallback field", t);
            }
        }

        return 0;
    }

    public static ImGuiContext ensureImGuiContext(ImGuiContext context) {
        if (context != null && !context.isNotValidPtr()) {
            return context;
        }

        ImGuiContext current = null;
        try {
            current = ImGui.getCurrentContext();
        } catch (Throwable ignored) {
        }
        if (current != null && !current.isNotValidPtr()) {
            return current;
        }

        try {
            ImGui.destroyContext();
        } catch (Throwable ignored) {
        }

        try {
            ImGuiContext created = ImGui.createContext();
            if (created != null && !created.isNotValidPtr()) {
                return created;
            }
        } catch (Throwable t) {
            LOGGER.warn("ImGuiCompat: failed to create fallback ImGui context", t);
        }

        return context;
    }

    private static boolean invokeVoid(ImGuiImplGl3 impl, Method method) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(impl);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("ImGuiCompat: invocation of {} failed", method.getName(), t);
            return false;
        }
    }
}