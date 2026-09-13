package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.chat.ChatTextShader;
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

    static void logCompileError(ShaderProgram program, RuntimeException why) {
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
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String textSource(String body) {
        return part("text_head") + body + part("text_main");
    }

    private static String windowSource(String body) {
        return part("window_head") + body + part("window_main");
    }

    private static String part(String name) {
        return ShaderProgram.readSource(Identifier.fromNamespaceAndPath("moud", "shaders/chat/" + name + ".glsl"));
    }
}
