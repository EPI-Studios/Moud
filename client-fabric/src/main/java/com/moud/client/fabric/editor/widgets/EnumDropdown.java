package com.moud.client.fabric.editor.widgets;

import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ComboBox;

public final class EnumDropdown {
    private final ComboBox<String> combo = new ComboBox<>();
    private EnumDropdownListener listener;
    private int lastSelected = -1;

    public void setItems(String... items) {
        combo.clear();
        if (items != null) {
            for (String s : items) combo.addItem(s);
        }
        if (combo.items().size() > 0 && combo.selectedIndex() < 0) combo.setSelectedIndex(0);
    }

    public void setSelected(String value) {
        for (int i = 0; i < combo.items().size(); i++) {
            if (combo.items().get(i).equals(value)) {
                combo.setSelectedIndex(i);
                lastSelected = i;
                return;
            }
        }
    }

    public String selected() { return combo.selected(); }
    public int selectedIndex() { return combo.selectedIndex(); }

    public void setListener(EnumDropdownListener l) { this.listener = l; }

    public int render(UiRenderer r, UiContext ctx, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        boolean changed = combo.render(r, ctx, input, theme, x, y, width, height, 200, height, interactive, true);
        if (changed && listener != null) {
            listener.onSelected(combo.selectedIndex(), combo.selected());
            lastSelected = combo.selectedIndex();
        }
        return y + height;
    }
}
