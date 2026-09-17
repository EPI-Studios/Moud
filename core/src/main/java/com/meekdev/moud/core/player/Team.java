package com.meekdev.moud.core.player;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.input.ClickDetector;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Team extends Instance {

    public Color teamColor = Color.WHITE;

    public boolean autoAssignable = true;

    public final Signal<ClickDetector.Clicker> playerAdded = new Signal<>();

    public final Signal<ClickDetector.Clicker> playerRemoved = new Signal<>();
}
