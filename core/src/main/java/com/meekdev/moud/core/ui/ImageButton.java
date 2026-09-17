package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class ImageButton extends ImageLabel {

    public final Signal<Instance> activated = new Signal<>();

    @Prop(asset = true) public String hoverImage = "";
    @Prop(asset = true) public String pressedImage = "";

    @Prop(replicated = false) public boolean hovered;
    @Prop(replicated = false) public boolean pressed;

    public boolean autoButtonColor = true;

    public String shownImage() {
        if (pressed && !pressedImage.isEmpty()) return pressedImage;
        if ((hovered || pressed) && !hoverImage.isEmpty()) return hoverImage;
        return image;
    }

    @Override
    public boolean sinksInput() {
        return true;
    }
}
