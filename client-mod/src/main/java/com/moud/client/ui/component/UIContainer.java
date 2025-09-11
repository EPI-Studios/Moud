package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;

public final class UIContainer extends UIComponent {
    private String flexDirection = "row";
    private String justifyContent = "flex-start";
    private String alignItems = "stretch";
    private double gap = 0;

    public UIContainer(UIService service) {
        super("container", service);
    }

    public UIContainer setFlexDirection(String direction) { this.flexDirection = direction; markDirty(); return this; }
    public String getFlexDirection() { return flexDirection; }

    public UIContainer setJustifyContent(String justify) { this.justifyContent = justify; markDirty(); return this; }
    public String getJustifyContent() { return justifyContent; }

    public UIContainer setAlignItems(String align) { this.alignItems = align; markDirty(); return this; }
    public String getAlignItems() { return alignItems; }

    public UIContainer setGap(double gap) { this.gap = gap; markDirty(); return this; }
    public double getGap() { return gap; }
}