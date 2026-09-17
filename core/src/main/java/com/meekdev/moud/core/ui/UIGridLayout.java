package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.UDim2;

public final class UIGridLayout extends UILayout {

    public UDim2 cellSize = UDim2.fromOffset(100, 100);
    public UDim2 cellPadding = UDim2.fromOffset(5, 5);

    public StartCorner startCorner = StartCorner.TOP_LEFT;

    @Prop(min = 0) public int fillDirectionMaxCells;

    public UIGridLayout() {
        fillDirection = FillDirection.HORIZONTAL;
    }
}
