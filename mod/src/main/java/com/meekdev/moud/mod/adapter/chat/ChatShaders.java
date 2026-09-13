package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.ChatTextShader;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

// the shaders a place draws chat through: one per ChatTextShader for text, and one for the whole window
//
// a file is read again at most once a second and recompiled when its text changed, so editing a shader
// while the game runs shows on the next second
final class ChatShaders {

    private static final Identifier UI_VERTEX = Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/ui.vsh");
    private static final Identifier FULLSCREEN = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");

    private static final class Compiled {
        final ShaderProgram program;
        final Identifier fragment;
        String source = "";
        long checked;
        boolean broken;

        Compiled(ShaderProgram program, Identifier fragment) {
            this.program = program;
            this.fragment = fragment;
        }
    }

    private static final Map<String, Compiled> COMPILED = new HashMap<>();
    private static final Map<Identifier, String> SOURCES = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private ChatShaders() {}

    // the program for <shader=name>, or null when no ChatTextShader has that name or its file cannot be read
    static @Nullable ShaderProgram text(String name) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return null;
        for (ChatTextShader shader : tree.ofClass(Classes.CHAT_TEXT_SHADER)) {
            if (!shader.name().equals(name) || shader.shader.isEmpty()) continue;
            return compiled("text:" + shader.shader, shader.shader, UI_VERTEX, ChatShaders::textSource);
        }
        return null;
    }

    static @Nullable ShaderProgram window(String file) {
        return file.isEmpty() ? null : compiled("window:" + file, file, FULLSCREEN, ChatShaders::windowSource);
    }

    static void broke(ShaderProgram program, RuntimeException why) {
        for (Compiled one : COMPILED.values()) {
            if (one.program != program || one.broken) continue;
            one.broken = true;
            if (WARNED.add(one.source)) MoudMod.LOG.warn("chat shader cannot be drawn: {}", why.getMessage());
        }
    }

    private static @Nullable ShaderProgram compiled(String key, String file, Identifier vertex,
                                                    Function<String, String> wrap) {
        Compiled known = COMPILED.get(key);
        long now = System.currentTimeMillis();
        if (known != null && now - known.checked < 1000) return known.broken ? null : known.program;
        String body = read(file);
        if (body == null) {
            if (WARNED.add(key)) MoudMod.LOG.warn("chat shader {} cannot be read", file);
            return known == null || known.broken ? null : known.program;
        }
        String source = wrap.apply(body);
        if (known == null) {
            Identifier fragment = Identifier.fromNamespaceAndPath("moud",
                    "shaders/chat/generated/" + Integer.toHexString(key.hashCode()) + ".fsh");
            ShaderProgram.registerVirtualSource(fragment, () -> SOURCES.get(fragment));
            known = new Compiled(new ShaderProgram(vertex, fragment), fragment);
            COMPILED.put(key, known);
        }
        known.checked = now;
        if (!known.source.equals(source)) {
            known.source = source;
            SOURCES.put(known.fragment, source);
            known.broken = false;
            known.program.invalidate();
        }
        return known.broken ? null : known.program;
    }

    private static @Nullable String read(String file) {
        if (file.startsWith(Res.SCHEME)) {
            byte[] bytes = PlaceFiles.read(file);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
        Identifier id = Identifier.tryParse(file);
        if (id == null) return null;
        try {
            return ShaderProgram.readSource(id);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    // the place's file defines vec4 textColor(vec4 color, vec2 uv, vec2 glyph, float time)
    private static String textSource(String body) {
        return """
                #version 330 core
                in vec2 vUv;
                in vec4 vColor;
                in vec2 vPos;
                flat in vec4 vParams;
                flat in vec2 vExtra;
                flat in vec4 vClip;
                uniform sampler2D Tex;
                uniform vec2 ScreenSize;
                uniform float Time;
                out vec4 FragColor;
                #line 1
                """ + body + """

                void main() {
                    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;
                    vec4 base = texture(Tex, vUv) * vColor;
                    vec4 rect = vec4(vParams.y, vParams.z, vParams.w, vExtra.x);
                    vec2 glyph = (vUv - rect.xy) / max(rect.zw - rect.xy, vec2(1e-6));
                    vec4 result = textColor(base, vPos / ScreenSize, glyph, Time);
                    if (result.a <= 0.001) discard;
                    FragColor = result;
                }
                """;
    }

    // the place's file defines vec4 windowColor(vec2 uv, vec2 local, float time), reading the picture
    // through scene(uv). local runs from 0 to 1 across the window
    private static String windowSource(String body) {
        return """
                #version 330 core
                in vec2 vUV;
                out vec4 FragColor;
                uniform sampler2D SceneColorSampler;
                uniform vec2 ScreenSize;
                uniform vec4 Rect;
                uniform float Time;
                vec4 scene(vec2 uv) { return texture(SceneColorSampler, uv); }
                #line 1
                """ + body + """

                void main() {
                    vec2 px = vec2(vUV.x, 1.0 - vUV.y) * ScreenSize;
                    vec2 local = (px - Rect.xy) / max(Rect.zw, vec2(1.0));
                    if (local.x < 0.0 || local.y < 0.0 || local.x > 1.0 || local.y > 1.0) discard;
                    FragColor = windowColor(vUV, local, Time);
                }
                """;
    }
}
