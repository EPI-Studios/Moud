package com.meekdev.moud.core.character;

import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;

public final class Animator extends Instance {

    public final Signal<Instance> animationPlayed = new Signal<>();
}
