package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;

public final class UIText extends UIComponent {
    public UIText(String content, UIService service) {
        super("text", service);
        setText(content);

        setBackgroundColor("#00000000");
    }
}