package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import org.graalvm.polyglot.HostAccess;

public class UIContainer extends UIComponent {
    private volatile String flexDirection = "row";
    private volatile String justifyContent = "flex-start";
    private volatile String alignItems = "stretch";
    private volatile double gap = 0;
    private volatile boolean autoResize = true;
    private volatile boolean isUpdatingLayout = false;

    public UIContainer(UIService service) {
        super("container", service);
        setBackgroundColor("#00000000");
    }

    @Override
    @HostAccess.Export
    public UIComponent appendChild(UIComponent child) {
        super.appendChild(child);
        updateLayout();
        return this;
    }

    @HostAccess.Export
    public UIContainer setFlexDirection(String direction) {
        this.flexDirection = direction;
        updateLayout();
        return this;
    }

    @HostAccess.Export
    public String getFlexDirection() {
        return flexDirection;
    }

    @HostAccess.Export
    public UIContainer setJustifyContent(String justify) {
        this.justifyContent = justify;
        updateLayout();
        return this;
    }

    @HostAccess.Export
    public String getJustifyContent() {
        return justifyContent;
    }

    @HostAccess.Export
    public UIContainer setAlignItems(String align) {
        this.alignItems = align;
        updateLayout();
        return this;
    }

    @HostAccess.Export
    public String getAlignItems() {
        return alignItems;
    }

    @HostAccess.Export
    public UIContainer setGap(double gap) {
        this.gap = gap;
        updateLayout();
        return this;
    }

    @HostAccess.Export
    public double getGap() {
        return gap;
    }

    @HostAccess.Export
    public UIContainer setAutoResize(boolean autoResize) {
        this.autoResize = autoResize;
        updateLayout();
        return this;
    }

    public void updateLayout() {
        if (isUpdatingLayout) return;

        isUpdatingLayout = true;
        try {
            if (children.isEmpty()) {
                if(autoResize) {
                    this.width = (float) (paddingLeft + paddingRight);
                    this.height = (float) (paddingTop + paddingBottom);
                }
                return;
            }

            float containerWidth = getWidth() - (float) paddingLeft - (float) paddingRight;
            float containerHeight = getHeight() - (float) paddingTop - (float) paddingBottom;

            if ("row".equalsIgnoreCase(flexDirection)) {
                layoutRow(containerWidth, containerHeight);
            } else {
                layoutColumn(containerWidth, containerHeight);
            }

            if (autoResize) {
                resizeToContent();
            }

        } finally {
            isUpdatingLayout = false;
        }
    }

    private void layoutRow(float containerWidth, float containerHeight) {
        float totalChildWidth = (float) children.stream().mapToDouble(UIComponent::getWidth).sum();
        float totalGaps = (children.size() > 1) ? (float)((children.size() - 1) * gap) : 0;
        float usedSpace = totalChildWidth + totalGaps;
        float availableSpace = autoResize ? 0 : containerWidth - usedSpace;

        float currentX = (float) paddingLeft;
        float spaceBetween = (float) gap;

        switch (justifyContent.toLowerCase()) {
            case "center": currentX += availableSpace / 2f; break;
            case "flex-end": currentX += availableSpace; break;
            case "space-between":
                if (children.size() > 1) {
                    spaceBetween += availableSpace / (children.size() - 1);
                }
                break;
            case "space-around":
                float space = availableSpace / children.size();
                currentX += space / 2f;
                spaceBetween += space;
                break;
        }

        for (UIComponent child : children) {

            float childY = (float) paddingTop;

            switch (alignItems.toLowerCase()) {
                case "center": childY += (containerHeight - child.getHeight()) / 2f; break;
                case "flex-end": childY += containerHeight - child.getHeight(); break;
                case "stretch":
                    if (!autoResize) {
                        child.setHeight(containerHeight);
                    }
                    break;
            }

            child.setPos(currentX, childY);
            currentX += child.getWidth() + spaceBetween;
        }
    }

    private void layoutColumn(float containerWidth, float containerHeight) {
        float totalChildHeight = (float) children.stream().mapToDouble(UIComponent::getHeight).sum();
        float totalGaps = (children.size() > 1) ? (float)((children.size() - 1) * gap) : 0;
        float usedSpace = totalChildHeight + totalGaps;
        float availableSpace = autoResize ? 0 : containerHeight - usedSpace;

        float currentY = (float) paddingTop;
        float spaceBetween = (float) gap;

        switch (justifyContent.toLowerCase()) {
            case "center": currentY += availableSpace / 2f; break;
            case "flex-end": currentY += availableSpace; break;
            case "space-between":
                if (children.size() > 1) {
                    spaceBetween += availableSpace / (children.size() - 1);
                }
                break;
            case "space-around":
                float space = availableSpace / children.size();
                currentY += space / 2f;
                spaceBetween += space;
                break;
        }

        for (UIComponent child : children) {

            float childX = (float) paddingLeft;

            switch (alignItems.toLowerCase()) {
                case "center": childX += (containerWidth - child.getWidth()) / 2f; break;
                case "flex-end": childX += containerWidth - child.getWidth(); break;
                case "stretch":
                    if (!autoResize) {
                        child.setWidth(containerWidth);
                    }
                    break;
            }

            child.setPos(childX, currentY);
            currentY += child.getHeight() + spaceBetween;
        }
    }

    private void resizeToContent() {
        if (children.isEmpty()) return;

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        for (UIComponent child : children) {
            minX = Math.min(minX, child.getX());
            minY = Math.min(minY, child.getY());
            maxX = Math.max(maxX, child.getX() + child.getWidth());
            maxY = Math.max(maxY, child.getY() + child.getHeight());
        }

        float newWidth = (maxX - minX) + (float) paddingLeft + (float) paddingRight;
        float newHeight = (maxY - minY) + (float) paddingTop + (float) paddingBottom;

        if (newWidth != getWidth() || newHeight != getHeight()) {
            this.width = newWidth;
            this.height = newHeight;
            updateLayout();
        }
    }
}