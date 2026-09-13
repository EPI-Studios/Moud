package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Callback;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;

public final class TextChannel extends Instance {

    public boolean autoJoin = true;

    public String displayName = "";
    public Color color = Color.WHITE;

    @Prop(min = 0) public double slowMode;

    @Prop(min = 1) public int maxLength = 256;

    public final Callback shouldDeliver = new Callback();

    public final Callback shouldSend = new Callback();

    public final Callback onIncoming = new Callback();

    public final Signal<Object> messageReceived = new Signal<>();

    public String richText = "";
}
