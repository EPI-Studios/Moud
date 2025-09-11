package com.moud.client.ui.layout;

import com.moud.client.ui.component.UIComponent;
import com.moud.client.ui.component.UIContainer;

import java.util.List;

public class LayoutEngine {

    public void updateLayout(UIComponent element) {
        if (!element.isDirty()) {
            return;
        }

        if (element instanceof UIContainer container) {
            layoutContainer(container);
        }

        for (UIComponent child : element.getChildren()) {
            updateLayout(child);
        }

        element.clearDirty();
    }

    private void layoutContainer(UIContainer container) {
        List<UIComponent> children = container.getChildren();
        if (children.isEmpty()) return;

        String flexDirection = container.getFlexDirection();
        String justifyContent = container.getJustifyContent();
        String alignItems = container.getAlignItems();
        double gap = container.getGap();

        double containerX = container.getX() + container.getPaddingLeft();
        double containerY = container.getY() + container.getPaddingTop();
        double containerWidth = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();
        double containerHeight = container.getHeight() - container.getPaddingTop() - container.getPaddingBottom();

        if ("row".equalsIgnoreCase(flexDirection)) {
            layoutRow(children, containerX, containerY, containerWidth, containerHeight, justifyContent, alignItems, gap);
        } else {
            layoutColumn(children, containerX, containerY, containerWidth, containerHeight, justifyContent, alignItems, gap);
        }
    }

    private void layoutRow(List<UIComponent> children, double containerX, double containerY,
                           double containerWidth, double containerHeight, String justifyContent,
                           String alignItems, double gap) {

        double totalChildWidth = children.stream().mapToDouble(UIComponent::getWidth).sum();
        double totalGaps = (children.size() > 1) ? (children.size() - 1) * gap : 0;
        double availableSpace = containerWidth - totalChildWidth - totalGaps;

        double currentX = containerX;

        switch (justifyContent.toLowerCase()) {
            case "center": currentX += availableSpace / 2; break;
            case "flex-end": currentX += availableSpace; break;
            case "space-between": gap += children.size() > 1 ? availableSpace / (children.size() - 1) : 0; break;
            case "space-around":
                double space = availableSpace / children.size();
                currentX += space / 2;
                gap += space;
                break;
        }

        for (UIComponent child : children) {
            double childY = containerY;

            switch (alignItems.toLowerCase()) {
                case "center": childY += (containerHeight - child.getHeight()) / 2; break;
                case "flex-end": childY += containerHeight - child.getHeight(); break;
            }

            child.setPosition((int) currentX, (int) childY);
            currentX += child.getWidth() + gap;
        }
    }

    private void layoutColumn(List<UIComponent> children, double containerX, double containerY,
                              double containerWidth, double containerHeight, String justifyContent,
                              String alignItems, double gap) {

        double totalChildHeight = children.stream().mapToDouble(UIComponent::getHeight).sum();
        double totalGaps = (children.size() > 1) ? (children.size() - 1) * gap : 0;
        double availableSpace = containerHeight - totalChildHeight - totalGaps;

        double currentY = containerY;

        switch (justifyContent.toLowerCase()) {
            case "center": currentY += availableSpace / 2; break;
            case "flex-end": currentY += availableSpace; break;
            case "space-between": gap += children.size() > 1 ? availableSpace / (children.size() - 1) : 0; break;
            case "space-around":
                double space = availableSpace / children.size();
                currentY += space / 2;
                gap += space;
                break;
        }

        for (UIComponent child : children) {
            double childX = containerX;

            switch (alignItems.toLowerCase()) {
                case "center": childX += (containerWidth - child.getWidth()) / 2; break;
                case "flex-end": childX += containerWidth - child.getWidth(); break;
            }

            child.setPosition((int) childX, (int) currentY);
            currentY += child.getHeight() + gap;
        }
    }
}