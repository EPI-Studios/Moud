package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;

public final class UIImage extends UIComponent {
    private String source;

    public UIImage(String source, UIService service) {
        super("image", service);
        this.source = source;
        setBackgroundColor("#00000000");
    }

    public UIImage setSource(String source) {
        this.source = source;
        markDirty();
        return this;
    }

    public String getSource() {
        return source;
    }
}