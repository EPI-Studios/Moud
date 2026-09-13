package com.meekdev.moud.core.chat;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.instance.Instance;

public final class ChatInputBar extends Instance {

    public boolean enabled = true;

    public Instance targetChannel;

    public Color backgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.5;
    public Color textColor = Color.WHITE;
    public String placeholder = "";
    public Color placeholderColor = new Color(0.6f, 0.6f, 0.6f, 1);
    @Prop(min = 1) public int maxLength = 256;

    public boolean autocomplete = true;
}
