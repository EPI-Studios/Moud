package com.meekdev.moud.script.host;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.script.ModuleScript;
import com.meekdev.moud.script.host.audio.AudioLibrary;
import com.meekdev.moud.script.host.chat.ChatLibrary;
import com.meekdev.moud.script.host.data.HttpLibrary;
import com.meekdev.moud.script.host.data.MessagingLibrary;
import com.meekdev.moud.script.host.data.StoreLibrary;
import com.meekdev.moud.script.host.debug.DebugLibrary;
import com.meekdev.moud.script.host.java.JavaLibrary;
import com.meekdev.moud.script.host.render.EffectLibrary;
import com.meekdev.moud.script.host.render.LightingLibrary;
import com.meekdev.moud.script.host.render.ShaderLibrary;
import com.meekdev.moud.script.host.player.Players;
import com.meekdev.moud.script.host.tween.TweenLibrary;
import com.meekdev.moud.script.host.world.Blocks;
import com.meekdev.moud.script.host.world.Debris;
import com.meekdev.moud.script.host.world.InstanceAttributes;
import com.meekdev.moud.script.host.world.Paths;
import com.meekdev.moud.script.host.world.RemoteFunctions;
import com.meekdev.moud.script.host.world.Trees;
import com.meekdev.moud.script.host.world.PartPhysics;
import com.meekdev.moud.script.host.world.WorldQueries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class Libraries {

    private Libraries() {}

    static void install(Host host) {
        host.api().declare(Connection.decl());
        host.api().declare(HostSignal.decl("AnySignal", "(...any) -> ()"));
        host.api().declare(HostSignal.decl("StepSignal", "(delta: number) -> ()"));
        host.api().declare(HostSignal.decl("ChangedSignal", "(property: string) -> ()"));
        host.api().declare(HostSignal.decl("InstanceSignal", "(instance: Instance) -> ()"));
        host.api().declare(HostSignal.decl("AncestrySignal", "(child: Instance, parent: Instance?) -> ()"));
        host.api().declare(HostSignal.decl("PlayerSignal", "(player: Player) -> ()"));
        host.api().declare(HostSignal.decl("ExplosionSignal", "(part: Instance, distance: number) -> ()"));
        host.api().declare(HostSignal.decl("RemoteServerSignal", "(body: Instance, ...any) -> ()"));
        host.api().declare(HostSignal.decl("RemotePlayerSignal", "(player: Player, ...any) -> ()"));
        host.api().declare(HostSignal.decl("ChatCommandSignal", "(body: Instance?, text: string, args: { string }) -> ()"));
        host.api().declare(HostSignal.decl("ChatMessageSignal", "(message: { [string]: any }) -> ()"));
        Api.Decl scripted = HostSignal.decl("Signal", "(...any) -> ()");
        List<Api.Member> signalMembers = new ArrayList<>(scripted.members());
        signalMembers.add(new Api.Member("fire", Api.Kind.METHOD, "(...any) -> ()"));
        signalMembers.add(new Api.Member("disconnectAll", Api.Kind.METHOD, "() -> ()"));
        host.api().declare(new Api.Decl("Signal", null, signalMembers));
        host.global("signal", "(name: string?) -> Signal", new Builtin("signal", a -> HostSignal.scripted(host, a.string(0, "signal"))));

        Members game = new Members("Game")
                .value("world", "Instance", host.world())
                .value("stepped", "StepSignal", host.stepped())
                .value("renderStepped", "StepSignal", host.renderStepped())
                .value("reloaded", "AnySignal", host.reloadedSignal())
                .field("persist", "{ [string]: any }", host::persistTable, host::persistTable);

        Values.install(host);
        Tasks.install(host);
        MathLibrary.install(host);
        WorldQueries.install(host);
        PartPhysics.install(host);
        Trees.install(host, game);
        InstanceAttributes.install(host);
        RemoteFunctions.install(host);
        Debris.install(host, game);
        Players.install(host, game);
        Paths.install(host, game);
        Blocks.install(host, game);
        AudioLibrary.install(host);
        TweenLibrary.install(host);
        JavaLibrary.install(host);
        ShaderLibrary.install(host);
        EffectLibrary.install(host);
        LightingLibrary.install(host);
        DebugLibrary.install(host, game);
        StoreLibrary.install(host);
        HttpLibrary.install(host);
        MessagingLibrary.install(host);
        GameLibrary.install(host, game);
        WindowLibrary.install(host);
        CoreGuiLibrary.install(host);
        InterfaceLibrary.install(host);
        if (host.chat() != null) host.chatLibrary(ChatLibrary.install(host, game));

        host.global("game", "Game", game);
        host.declare(game);
        host.global("print", "(...any) -> ()", new Builtin("print", a -> {
            StringBuilder line = new StringBuilder();
            for (int n = 0; n < a.count(); n++) {
                if (n > 0) line.append('\t');
                line.append(host.text(a.get(n)));
            }
            host.print(line.toString());
            return null;
        }));
        installRequire(host);

        host.api().declare(host.instances().shared().decl());
        for (Map.Entry<ClassDef<?>, Members> entry : host.instances().classMembers().entrySet()) {
            host.api().declare(entry.getValue().decl());
        }
    }

    private static void installRequire(Host host) {
        Map<Object, Object> cache = new HashMap<>();
        Set<Object> loading = new HashSet<>();
        host.global("require", "(module: string | Instance) -> any", new Builtin("require", a -> {
            if (a.get(0) instanceof ModuleScript module) {
                if (!module.isAlive()) throw new HostError("%s has been destroyed", module.name());
                Object cached = cache.get(module);
                if (cached != null) return cached;
                if (module.code.isEmpty() && module.source.isEmpty()) throw new HostError("%s has no code and no source", module.name());
                if (!module.code.isEmpty()) return load(host, cache, loading, module, fullName(module), module.code, module);
                Host.Script found = find(host, module.source);
                return load(host, cache, loading, module, found.path(), found.code(), module);
            }
            if (a.get(0) instanceof Instance other) throw new HostError("require expects a ModuleScript, got a %s", other.def().name());
            Host.Script found = find(host, a.string(0));
            return load(host, cache, loading, found.path(), found.path(), found.code(), null);
        }));
        host.onClose(() -> {
            for (Object module : cache.values()) {
                if (module instanceof ScriptValue value) value.release();
            }
        });
    }

    private static Host.Script find(Host host, String text) {
        String path;
        try {
            path = Res.script(text.startsWith("@") ? Res.SCHEME + text.substring(1) : text);
        } catch (IllegalArgumentException e) {
            throw new HostError(e.getMessage());
        }
        Host.Script found = host.readScript(path);
        if (found == null) throw new HostError("there is no res://%s", path);
        return found;
    }

    private static Object load(Host host, Map<Object, Object> cache, Set<Object> loading, Object key, String chunk, String code, Instance script) {
        Object cached = cache.get(key);
        if (cached != null) return cached;
        if (!loading.add(key)) throw new HostError("circular require of %s", key instanceof Instance module ? fullName(module) : "res://" + chunk);
        try {
            Object module = host.engine().module(chunk, code, script);
            cache.put(key, module);
            return module;
        } finally {
            loading.remove(key);
        }
    }

    private static String fullName(Instance instance) {
        StringBuilder path = new StringBuilder(instance.name());
        for (Instance up = instance.parent(); up != null; up = up.parent()) path.insert(0, up.name() + ".");
        return path.toString();
    }
}
