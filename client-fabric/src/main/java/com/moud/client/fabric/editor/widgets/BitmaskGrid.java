package com.moud.client.fabric.editor.widgets;

import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

public final class BitmaskGrid {
    private static final int BITS = 32;
    private static final int COLS = 8;
    private static final int ROWS = BITS / COLS;

    private int mask;
    private String[] labels = new String[BITS];
    private BitmaskListener listener;

    public BitmaskGrid() {
        for (int i = 0; i < BITS; i++) labels[i] = String.valueOf(i + 1);
    }

    public void setMask(int m) { this.mask = m; }
    public int mask() { return mask; }
    public void setLabel(int bit, String label) {
        if (bit >= 0 && bit < BITS) labels[bit] = label == null ? String.valueOf(bit + 1) : label;
    }
    public void setListener(BitmaskListener l) { this.listener = l; }

    public int rows() { return ROWS; }
    public int cellHeight() { return 18; }

    public int render(UiRenderer r, UiInput input, Theme theme,
                      int x, int y, int width, int rowHeight, boolean interactive) {
        int gap = 2;
        int cellW = (width - gap * (COLS - 1)) / COLS;
        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        boolean clicked = canInteract && input.mousePressed();

        for (int i = 0; i < BITS; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int cx = x + col * (cellW + gap);
            int cy = y + row * (rowHeight + gap);
            boolean on = ((mask >>> i) & 1) != 0;
            boolean over = canInteract && mx >= cx && mx < cx + cellW && my >= cy && my < cy + rowHeight;
            int bg = on
                    ? Theme.toArgb(theme.widgetActive)
                    : (over ? Theme.toArgb(theme.widgetHover) : Theme.toArgb(theme.widgetBg));
            r.drawRoundedRect(cx, cy, cellW, rowHeight, theme.design.radius_sm, bg);
            int textColor = on ? 0xFF101015 : Theme.toArgb(theme.text);
            String lbl = labels[i];
            float tw = r.measureText(lbl);
            r.drawText(lbl, cx + (cellW - tw) / 2f, r.baselineForBox(cy, rowHeight), textColor);
            if (over && clicked) {
                mask ^= (1 << i);
                if (listener != null) listener.onChanged(mask);
            }
        }
        return y + ROWS * rowHeight + (ROWS - 1) * gap;
    }
}
