package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// the box a player types in. the first one in the tree is used
public final class ChatInputBar extends Instance {

    // whether the chat can be opened at all
    public boolean enabled = true;

    // where what is typed goes. empty is the first channel the player is in
    public Instance targetChannel;

    public Color backgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.5;
    public Color textColor = Color.WHITE;
    public String placeholder = "";
    public Color placeholderColor = new Color(0.6f, 0.6f, 0.6f, 1);
    @Prop(min = 1) public int maxLength = 256;

    // whether commands are suggested as they are typed
    public boolean autocomplete = true;
}
