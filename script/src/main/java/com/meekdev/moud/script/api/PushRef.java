package com.meekdev.moud.script.api;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.math.Vector3;

public interface PushRef {

    void push(Character body, Vector3 perSecond, boolean replace);
}
