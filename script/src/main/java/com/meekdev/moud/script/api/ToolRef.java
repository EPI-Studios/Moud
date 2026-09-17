package com.meekdev.moud.script.api;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Tool;

public interface ToolRef {

    void equip(Character body, Tool tool);

    void unequip(Character body);
}
