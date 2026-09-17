package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;

public final class TextBox extends TextLabel {

    public enum Request { NONE, CAPTURE, RELEASE }

    public String placeholderText = "";
    public Color placeholderColor = new Color(0.7f, 0.7f, 0.7f, 1f);

    public boolean clearTextOnFocus = true;
    public boolean multiLine;
    public boolean textEditable = true;

    @Prop(replicated = false) public int cursorPosition = -1;
    @Prop(replicated = false) public int selectionStart = -1;

    public final Signal<Object[]> focused = new Signal<>();
    public final Signal<Object[]> focusLost = new Signal<>();

    private boolean holding;
    private Request request = Request.NONE;

    public void captureFocus() {
        request = Request.CAPTURE;
    }

    public void releaseFocus() {
        request = Request.RELEASE;
    }

    public boolean isFocused() {
        return holding;
    }

    public Request takeRequest() {
        Request taken = request;
        request = Request.NONE;
        return taken;
    }

    public void gainedFocus() {
        holding = true;
        focused.fire(new Object[0]);
    }

    public void lostFocus(boolean enterPressed) {
        holding = false;
        focusLost.fire(new Object[] {enterPressed});
    }

    @Override
    public boolean sinksInput() {
        return true;
    }
}
