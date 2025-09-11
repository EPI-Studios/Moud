package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.client.util.InputUtil;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;

public final class UIInput extends UIComponent {

    private String value = "";
    private final String placeholder;
    private boolean focused = false;

    public UIInput(String placeholder, UIService service) {
        super("input", service);
        this.placeholder = placeholder;

        setBackgroundColor("#FFFFFF");
        setBorder(1, "#A0A0A0");
        setTextAlign("left");
        setPadding(0, 4, 0, 4);
    }

    @Override
    public String getText() {
        if (!value.isEmpty()) {
            return value;
        }

        return focused ? "" : placeholder;
    }

    public String getValue() {
        return value;
    }

    public UIInput setValue(String value) {
        String oldValue = this.value;
        this.value = value == null ? "" : value;
        markDirty();
        triggerChange(this.value, oldValue);
        return this;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public boolean isFocused() {
        return focused;
    }

    public UIInput setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            if (focused) {
                triggerFocus();
            } else {
                triggerBlur();
            }
            markDirty();
        }
        return this;
    }

    public UIInput onChange(Value callback) {
        addEventHandler("change", callback);
        return this;
    }

    private void triggerChange(String newValue, String oldValue) {
        executeEventHandler("change", this, newValue, oldValue);
    }

    public void handleCharTyped(char character) {
        if (focused) {
            setValue(this.value + character);
        }
    }

    public void handleKeyPressed(int keyCode) {
        if (focused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) {
                    setValue(value.substring(0, value.length() - 1));
                }
            }

        }
    }
}