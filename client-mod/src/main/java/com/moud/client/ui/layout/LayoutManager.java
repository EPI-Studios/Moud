package com.moud.client.ui.layout;

import com.moud.client.api.service.UIService;

import java.util.List;

public class LayoutManager {

    public void updateLayout(UIService.UIElement element) {
        if (!element.isDirty()) {
            return;
        }

        if (element instanceof UIService.UIContainer container) {
            layoutContainer(container);
        }

        for (UIService.UIElement child : element.getChildren()) {
            updateLayout(child);
        }

        element.clearDirty();
    }

    private void layoutContainer(UIService.UIContainer container) {
        List<UIService.UIElement> children = container.getChildren();
        if (children.isEmpty()) return;

        String flexDirection = container.getFlexDirection();
        String justifyContent = container.getJustifyContent();
        String alignItems = container.getAlignItems();
        double gap = container.getGap();

        double containerX = container.getX() + container.getPaddingLeft();
        double containerY = container.getY() + container.getPaddingTop();
        double containerWidth = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();
        double containerHeight = container.getHeight() - container.getPaddingTop() - container.getPaddingBottom();

        if ("row".equals(flexDirection)) {
            layoutRow(children, containerX, containerY, containerWidth, containerHeight, justifyContent, alignItems, gap);
        } else if ("column".equals(flexDirection)) {
            layoutColumn(children, containerX, containerY, containerWidth, containerHeight, justifyContent, alignItems, gap);
        }
    }

    private void layoutRow(List<UIService.UIElement> children, double containerX, double containerY,
                           double containerWidth, double containerHeight, String justifyContent,
                           String alignItems, double gap) {

        double totalChildWidth = children.stream()
                .mapToDouble(UIService.UIElement::getWidth)
                .sum();
        double totalGaps = (children.size() - 1) * gap;
        double availableSpace = containerWidth - totalChildWidth - totalGaps;

        double currentX = containerX;

        if ("center".equals(justifyContent)) {
            currentX += availableSpace / 2;
        } else if ("flex-end".equals(justifyContent)) {
            currentX += availableSpace;
        } else if ("space-between".equals(justifyContent)) {
            gap = children.size() > 1 ? availableSpace / (children.size() - 1) : 0;
        } else if ("space-around".equals(justifyContent)) {
            double spacing = availableSpace / children.size();
            currentX += spacing / 2;
            gap += spacing;
        }

        for (UIService.UIElement child : children) {
            double childY = containerY;

            if ("center".equals(alignItems)) {
                childY += (containerHeight - child.getHeight()) / 2;
            } else if ("flex-end".equals(alignItems)) {
                childY += containerHeight - child.getHeight();
            }

            child.setPosition(currentX, childY);
            currentX += child.getWidth() + gap;
        }
    }

    private void layoutColumn(List<UIService.UIElement> children, double containerX, double containerY,
                              double containerWidth, double containerHeight, String justifyContent,
                              String alignItems, double gap) {

        double totalChildHeight = children.stream()
                .mapToDouble(UIService.UIElement::getHeight)
                .sum();
        double totalGaps = (children.size() - 1) * gap;
        double availableSpace = containerHeight - totalChildHeight - totalGaps;

        double currentY = containerY;

        if ("center".equals(justifyContent)) {
            currentY += availableSpace / 2;
        } else if ("flex-end".equals(justifyContent)) {
            currentY += availableSpace;
        } else if ("space-between".equals(justifyContent)) {
            gap = children.size() > 1 ? availableSpace / (children.size() - 1) : 0;
        } else if ("space-around".equals(justifyContent)) {
            double spacing = availableSpace / children.size();
            currentY += spacing / 2;
            gap += spacing;
        }

        for (UIService.UIElement child : children) {
            double childX = containerX;

            if ("center".equals(alignItems)) {
                childX += (containerWidth - child.getWidth()) / 2;
            } else if ("flex-end".equals(alignItems)) {
                childX += containerWidth - child.getWidth();
            }

            child.setPosition(childX, currentY);
            currentY += child.getHeight() + gap;
        }
    }
}