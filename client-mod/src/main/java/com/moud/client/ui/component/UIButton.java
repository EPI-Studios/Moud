package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;

public final class UIButton extends UIComponent {
    public UIButton(String text, UIService service) {
        super("button", service);
        setText(text);
        setBackgroundColor("#C0C0C0");
        setBorder(1, "#808080");
        setTextAlign("center");
    }
}