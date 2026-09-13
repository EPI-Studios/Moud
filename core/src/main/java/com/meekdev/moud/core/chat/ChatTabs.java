package com.meekdev.moud.core.chat;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.instance.Instance;

public final class ChatTabs extends Instance {

    public boolean enabled = true;
    public Color backgroundColor = Color.BLACK;
    public Color selectedColor = new Color(0.25f, 0.25f, 0.25f, 1);
    public Color textColor = Color.WHITE;
    public Color unreadColor = new Color(1, 0.8f, 0.2f, 1);
}
