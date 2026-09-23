package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;

public final class CheckBox extends GuiObject {

    public final Signal<Boolean> toggled = new Signal<>();

    public boolean checked;

    public Color checkColor = new Color(0.1f, 0.1f, 0.1f, 1f);
    @Prop(asset = true) public String checkedImage = "";
    @Prop(asset = true) public String uncheckedImage = "";

    @Prop(replicated = false) public boolean hovered;
    @Prop(replicated = false) public boolean pressed;

    public boolean autoButtonColor = true;

    public CheckBox() {
        size = UDim2.fromOffset(16, 16);
        cornerRadius = 3;
    }

    public String shownImage() {
        return checked ? checkedImage : uncheckedImage;
    }

    @Override
    public boolean sinksInput() {
        return true;
    }
}
