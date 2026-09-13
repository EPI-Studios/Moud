package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class Armour extends Instance {

    @Prop(driven = true, asset = true) public String head = "";
    @Prop(driven = true, asset = true) public String chest = "";
    @Prop(driven = true, asset = true) public String legs = "";
    @Prop(driven = true, asset = true) public String feet = "";

    @Prop(driven = true, asset = true) public String hat = "";

    @Prop(driven = true) public boolean hatLayered;

    @Override
    protected long propertiesFromElsewhere() {
        return parent() instanceof Character body && body.worn() ? def().driven() : 0;
    }
}
