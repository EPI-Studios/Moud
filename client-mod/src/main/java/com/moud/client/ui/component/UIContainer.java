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
                    this.width = (int) (paddingLeft + paddingRight);
                    this.height = (int) (paddingTop + paddingBottom);
                }
                return;
            }

            int containerX = getX() + (int) paddingLeft;
            int containerY = getY() + (int) paddingTop;
            int containerWidth = getWidth() - (int) paddingLeft - (int) paddingRight;
            int containerHeight = getHeight() - (int) paddingTop - (int) paddingBottom;

            if ("row".equalsIgnoreCase(flexDirection)) {
                layoutRow(containerX, containerY, containerWidth, containerHeight);
            } else {
                layoutColumn(containerX, containerY, containerWidth, containerHeight);
            }

            if (autoResize) {
                resizeToContent();
            }

        } finally {
            isUpdatingLayout = false;
        }
    }

    private void layoutRow(int containerX, int containerY, int containerWidth, int containerHeight) {
        int totalChildWidth = children.stream().mapToInt(UIComponent::getWidth).sum();
        int totalGaps = (children.size() > 1) ? (int)((children.size() - 1) * gap) : 0;
        int usedSpace = totalChildWidth + totalGaps;
        int availableSpace = autoResize ? 0 : containerWidth - usedSpace;

        int currentX = containerX;
        double spaceBetween = gap;

        switch (justifyContent.toLowerCase()) {
            case "center": currentX += availableSpace / 2; break;
            case "flex-end": currentX += availableSpace; break;
            case "space-between":
                if (children.size() > 1) {
                    spaceBetween += (double) availableSpace / (children.size() - 1);
                }
                break;
            case "space-around":
                double space = (double) availableSpace / children.size();
                currentX += (int) (space / 2);
                spaceBetween += space;
                break;
        }

        for (UIComponent child : children) {
            int childY = containerY;

            switch (alignItems.toLowerCase()) {
                case "center": childY += (containerHeight - child.getHeight()) / 2; break;
                case "flex-end": childY += containerHeight - child.getHeight(); break;
                case "stretch":
                    if (!autoResize) {
                        child.setHeight(containerHeight);
                    }
                    break;
            }

            child.setPos(currentX, childY);
            currentX += child.getWidth() + (int) spaceBetween;
        }
    }

    private void layoutColumn(int containerX, int containerY, int containerWidth, int containerHeight) {
        int totalChildHeight = children.stream().mapToInt(UIComponent::getHeight).sum();
        int totalGaps = (children.size() > 1) ? (int)((children.size() - 1) * gap) : 0;
        int usedSpace = totalChildHeight + totalGaps;
        int availableSpace = autoResize ? 0 : containerHeight - usedSpace;


        int currentY = containerY;
        double spaceBetween = gap;

        switch (justifyContent.toLowerCase()) {
            case "center": currentY += availableSpace / 2; break;
            case "flex-end": currentY += availableSpace; break;
            case "space-between":
                if (children.size() > 1) {
                    spaceBetween += (double) availableSpace / (children.size() - 1);
                }
                break;
            case "space-around":
                double space = (double) availableSpace / children.size();
                currentY += (int) (space / 2);
                spaceBetween += space;
                break;
        }

        for (UIComponent child : children) {
            int childX = containerX;

            switch (alignItems.toLowerCase()) {
                case "center": childX += (containerWidth - child.getWidth()) / 2; break;
                case "flex-end": childX += containerWidth - child.getWidth(); break;
                case "stretch":
                    if (!autoResize) {
                        child.setWidth(containerWidth);
                    }
                    break;
            }

            child.setPos(childX, currentY);
            currentY += child.getHeight() + (int) spaceBetween;
        }
    }

    private void resizeToContent() {
        if (children.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (UIComponent child : children) {
            minX = Math.min(minX, child.getX());
            minY = Math.min(minY, child.getY());
            maxX = Math.max(maxX, child.getX() + child.getWidth());
            maxY = Math.max(maxY, child.getY() + child.getHeight());
        }

        int newWidth = (maxX - minX) + (int) paddingLeft + (int) paddingRight;
        int newHeight = (maxY - minY) + (int) paddingTop + (int) paddingBottom;


        if (newWidth != getWidth() || newHeight != getHeight()) {
            this.width = newWidth;
            this.height = newHeight;
            updateLayout();
        }
    }
}