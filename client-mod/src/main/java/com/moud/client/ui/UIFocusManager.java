package com.moud.client.ui;

import com.moud.client.ui.component.UIComponent;
import org.jetbrains.annotations.Nullable;

public final class UIFocusManager {
    @Nullable
    private static UIComponent focusedComponent;

    private UIFocusManager() {}

    @Nullable
    public static UIComponent getFocusedComponent() {
        return focusedComponent;
    }

    public static void setFocus(@Nullable UIComponent componentToFocus) {
        if (focusedComponent == componentToFocus) {
            return;
        }

        if (focusedComponent != null) {
            focusedComponent.triggerBlur();
        }

        focusedComponent = componentToFocus;

        if (focusedComponent != null) {
            focusedComponent.triggerFocus();
        }
    }

    public static void clearFocus() {
        setFocus(null);
    }
}