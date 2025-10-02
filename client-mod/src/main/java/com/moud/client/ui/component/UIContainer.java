package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.text.Text;
import org.graalvm.polyglot.HostAccess; // <-- Make sure this is imported

public class UIContainer extends UIComponent {
    private String flexDirection = "row";
    private String justifyContent = "flex-start";
    private String alignItems = "stretch";
    private double gap = 0;
    private boolean autoResize = true;
    private boolean isUpdatingLayout = false;

    public UIContainer(UIService service) {
        super("container", service, 0, 0, 100, 100, Text.literal(""));
        setBackgroundColor("#00000000");
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
        return this;
    }

    @Override
    protected void updateChildrenPositions() {
        super.updateChildrenPositions();
        updateLayout();
    }

    public void updateLayout() {
        if (children.isEmpty() || isUpdatingLayout) return;

        isUpdatingLayout = true;
        try {
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
        int availableSpace = containerWidth - totalChildWidth - totalGaps;

        int currentX = containerX;
        double gapIncrement = gap;

        switch (justifyContent.toLowerCase()) {
            case "center" -> currentX += availableSpace / 2;
            case "flex-end" -> currentX += availableSpace;
            case "space-between" -> {
                if (children.size() > 1) {
                    gapIncrement += (double) availableSpace / (children.size() - 1);
                }
            }
            case "space-around" -> {
                double space = (double) availableSpace / children.size();
                currentX += (int) (space / 2);
                gapIncrement += space;
            }
        }

        for (UIComponent child : children) {
            int childY = containerY;

            switch (alignItems.toLowerCase()) {
                case "center" -> childY += (containerHeight - child.getHeight()) / 2;
                case "flex-end" -> childY += containerHeight - child.getHeight();
                case "stretch" -> {
                    child.setSize(child.getWidth(), containerHeight);
                }
            }

            child.setPos(currentX, childY); // Changed from setPosition to setPos
            currentX += child.getWidth() + (int) gapIncrement;
        }
    }

    private void layoutColumn(int containerX, int containerY, int containerWidth, int containerHeight) {
        int totalChildHeight = children.stream().mapToInt(UIComponent::getHeight).sum();
        int totalGaps = (children.size() > 1) ? (int)((children.size() - 1) * gap) : 0;
        int availableSpace = containerHeight - totalChildHeight - totalGaps;

        int currentY = containerY;
        double gapIncrement = gap;

        switch (justifyContent.toLowerCase()) {
            case "center" -> currentY += availableSpace / 2;
            case "flex-end" -> currentY += availableSpace;
            case "space-between" -> {
                if (children.size() > 1) {
                    gapIncrement += (double) availableSpace / (children.size() - 1);
                }
            }
            case "space-around" -> {
                double space = (double) availableSpace / children.size();
                currentY += (int) (space / 2);
                gapIncrement += space;
            }
        }

        for (UIComponent child : children) {
            int childX = containerX;

            switch (alignItems.toLowerCase()) {
                case "center" -> childX += (containerWidth - child.getWidth()) / 2;
                case "flex-end" -> childX += containerWidth - child.getWidth();
                case "stretch" -> {
                    child.setSize(containerWidth, child.getHeight());
                }
            }

            child.setPos(childX, currentY); // Changed from setPosition to setPos
            currentY += child.getHeight() + (int) gapIncrement;
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

        int newWidth = maxX - getX() + (int) paddingRight;
        int newHeight = maxY - getY() + (int) paddingBottom;

        if (newWidth != getWidth() || newHeight != getHeight()) {
            setWidth(newWidth);
            setHeight(newHeight);
        }
    }
}