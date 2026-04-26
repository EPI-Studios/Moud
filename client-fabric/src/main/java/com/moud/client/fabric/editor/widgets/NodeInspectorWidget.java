package com.moud.client.fabric.editor.widgets;

import com.moud.core.PropertyDef;

public interface NodeInspectorWidget {
    boolean handles(String propertyKey);

    int renderRow(InspectorContext ctx,
                  PropertyDef property,
                  String value,
                  int x,
                  int y,
                  int width,
                  int rowHeight,
                  int labelWidth);
}
