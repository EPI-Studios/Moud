package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class Appearance extends Instance {

    @Prop(asset = true) public String skin = "";

    @Prop(driven = true) public boolean slim;

    @Prop(driven = true) public boolean ears;

    @Prop(replicated = false) public CharacterDisplay display = CharacterDisplay.MODEL;

    @Prop(replicated = false) public FirstPerson firstPerson = FirstPerson.ARM;

    @Override
    protected long propertiesFromElsewhere() {
        return parent() instanceof Character body && body.worn() ? def().driven() : 0;
    }
}
