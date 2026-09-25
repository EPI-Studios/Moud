package com.meekdev.moud.mod.client.zone;

import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ZoneSounds {

    private static final Map<Zone, Sound> PLAYING = new HashMap<>();
    private static final PropertyDef SOUND_ID = Classes.SOUND.property("soundId");
    private static final PropertyDef VOLUME = Classes.SOUND.property("volume");

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
        Zone zone = winner;
        PLAYING.entrySet().removeIf(entry -> {
            Sound sound = entry.getValue();
            if (entry.getKey() == zone && sound.isAlive()) return false;
            if (sound.isAlive()) Instances.destroy(sound);
            return true;
        });
        if (zone == null) return;
        Sound playing = PLAYING.get(zone);
        if (playing != null) {
            Instances.setObj(playing, SOUND_ID, zone.soundId);
            Instances.setNum(playing, VOLUME, zone.volume);
            return;
        }
        Sound sound = Instances.createLocal(Classes.SOUND, tree.root(), "zone sound");
        Instances.setObj(sound, SOUND_ID, zone.soundId);
        Instances.setNum(sound, VOLUME, zone.volume);
        Instances.setBool(sound, Classes.SOUND.property("looped"), true);
        Instances.setBool(sound, Classes.SOUND.property("playing"), true);
        PLAYING.put(zone, sound);
    }
}
