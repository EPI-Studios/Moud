package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;

public final class Tool extends Instance {

    public boolean enabled = true;

    public boolean requiresHandle;

    public boolean canBeDropped = true;

    public boolean manualActivationOnly;

    @Prop(asset = true) public String item = "";

    public String toolTip = "";

    public CFrame grip = CFrame.IDENTITY;

    public final Signal<Instance> activated = new Signal<>();

    public final Signal<Instance> deactivated = new Signal<>();

    public final Signal<Instance> equipped = new Signal<>();

    public final Signal<Instance> unequipped = new Signal<>();
}
