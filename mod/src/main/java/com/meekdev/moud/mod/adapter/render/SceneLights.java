package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.light.FalloffCurve;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightStyles;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.AreaLight;
import com.meekdev.moud.core.instance.AreaShape;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.LightSource;
import com.meekdev.moud.core.instance.SpotLight;
import com.meekdev.moud.core.instance.TubeLight;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

public final class SceneLights {

    private record Held(LightSource instance, Light light) {}

    private record Style(int id, String body, long checked) {}

    private static final Map<Integer, Held> LIGHTS = new HashMap<>();
    private static final Map<String, Style> STYLES = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private SceneLights() {}

    public static void frame(float partialTick) {
        InstanceTree tree = ClientScene.tree();
        Set<Integer> seen = new HashSet<>();
        if (tree != null) {
            for (LightSource light : tree.ofClass(Classes.LIGHT)) {
                seen.add(light.id());
                Held held = LIGHTS.get(light.id());
                if (held == null || held.instance() != light) {
                    if (held != null) held.light().remove();
                    held = new Held(light, create(light));
                    LIGHTS.put(light.id(), held);
                }
                copy(light, held.light(), partialTick);
            }
        }
        for (Iterator<Map.Entry<Integer, Held>> it = LIGHTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Held> entry = it.next();
            if (seen.contains(entry.getKey())) continue;
            entry.getValue().light().remove();
            it.remove();
        }
    }

    private static Light create(LightSource light) {
        Vec3 origin = Vec3.ZERO;
        Vector3f down = new Vector3f(0, 0, -1);
        return switch (light) {
            case SpotLight spot -> Lights.spot(origin, down, 20, 35, 1, 1, 1, 12, 1);
            case AreaLight area -> area.shape == AreaShape.DISC
                    ? Lights.areaDisc(origin, down, 0.5f, 1, 1, 1, 12, 1)
                    : Lights.areaRect(origin, down, 0.5f, 0.5f, 1, 1, 1, 12, 1);
            case TubeLight tube -> Lights.tube(origin, new Vector3f(1, 0, 0), 1, 1, 1, 1, 12, 1);
            default -> Lights.point(origin, 1, 1, 1, 12, 1);
        };
    }

    private static void copy(LightSource from, Light to, float partialTick) {
        CFrame world = ClientScene.motion().sample(from, partialTick);
        Vector3 at = world.position();
        Vector3 forward = world.rotation().rotate(Vector3.FORWARD);
        to.setPosition(at.x(), at.y(), at.z())
                .setDirection((float) forward.x(), (float) forward.y(), (float) forward.z())
                .setEnabled(from.enabled)
                .setRange((float) from.range)
                .setIntensity((float) from.brightness)
                .setFalloff(FalloffCurve.values()[from.falloff.ordinal()], (float) from.falloffExponent)
                .castsShadow(from.shadows)
                .shadowStrength((float) from.shadowStrength)
                .godray((float) from.godrays)
                .style(style(from.shader));
        if (from.temperature > 0) {
            to.setTemperature((float) from.temperature);
        } else {
            to.setColor(from.color.r(), from.color.g(), from.color.b());
        }
        switch (from) {
            case SpotLight spot -> to.setSpotAngles((float) spot.innerAngle, (float) spot.outerAngle);
            case AreaLight area -> {
                boolean disc = to.type() == LightType.AREA_DISC;
                if (disc != (area.shape == AreaShape.DISC)) {
                    LIGHTS.remove(from.id());
                    to.remove();
                    return;
                }
                float w = (float) area.width / 2;
                to.setAreaSize(w, disc ? w : (float) area.height / 2);
            }
            case TubeLight tube -> {
                Vector3 along = world.rotation().rotate(Vector3.RIGHT);
                to.setTangent((float) along.x(), (float) along.y(), (float) along.z())
                        .setTubeLength((float) tube.length);
            }
            default -> {}
        }
    }

    private static int style(String shader) {
        if (shader.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        Style known = STYLES.get(shader);
        if (known != null && now - known.checked() < 1000) return known.id();
        String body = read(shader);
        if (body == null) {
            if (MISSING.add(shader)) MoudMod.LOG.warn("light shader {} cannot be read", shader);
            return known == null ? 0 : known.id();
        }
        MISSING.remove(shader);
        if (known != null && known.body().equals(body)) {
            STYLES.put(shader, new Style(known.id(), body, now));
            return known.id();
        }
        int id = LightStyles.register("moud:" + shader, body);
        STYLES.put(shader, new Style(id, body, now));
        return id;
    }

    private static String read(String shader) {
        if (shader.startsWith(Res.SCHEME)) {
            byte[] bytes = PlaceFiles.read(shader);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
        Identifier id = Identifier.tryParse(shader);
        if (id == null) return null;
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
