package com.meekdev.moud.mod.client.zone;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.zone.Zones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.meekdev.moud.mod.client.ClientScene;

public final class ZoneSounds {

    private static final Map<Zone, Sound> PLAYING = new HashMap<>();

    private ZoneSounds() {}

    public static void tick(InstanceTree tree) {
        Character me = ClientScene.own();
        Zone winner = null;
        if (me != null) {
            List<Zone> here = Zones.at(tree, Transforms.world(me).position());
            for (Zone zone : here) {
                if (!zone.soundId.isEmpty()) {
                    winner = zone;
                    break;
                }
            }
        }
        for (Map.Entry<Zone, Sound> entry : Map.copyOf(PLAYING).entrySet()) {
            if (entry.getKey() == winner && entry.getValue().isAlive()) continue;
            if (entry.getValue().isAlive()) Instances.destroy(entry.getValue());
            PLAYING.remove(entry.getKey());
        }
        if (winner == null || PLAYING.containsKey(winner)) return;
        Zone zone = winner;
        Sound sound = Instances.createLocal(Classes.SOUND, tree.root(), "zone sound");
        Instances.setObj(sound, Classes.SOUND.property("soundId"), zone.soundId);
        Instances.setNum(sound, Classes.SOUND.property("volume"), zone.volume);
        Instances.setBool(sound, Classes.SOUND.property("looped"), true);
        Instances.setBool(sound, Classes.SOUND.property("playing"), true);
        PLAYING.put(zone, sound);
    }
}
