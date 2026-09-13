package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.api.ChatRef;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.api.StoreRef;
import com.meekdev.moud.script.err.ScriptError;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ScriptEngine extends AutoCloseable {

    void bind(Instance world, ClassRegistry classes);

    void bindPost(PostRef post, boolean client);

    void bindClient(Instance camera, CameraRef lens, InputRef input, Supplier<Instance> own);

    void bindModules(ModuleSource source);

    void bindAudio(AudioRef audio);

    void bindBlocks(BlockRef blocks);

    void bindFiles(FileRef files);

    void bindStore(StoreRef store);

    void bindChat(ChatRef chat);

    void bindDebug(DebugRef debug);

    Object[] chatHook(String name, Object... args);

    void chatEvent(String name, Object... args);

    void bindHistory(HistoryRef history);

    void runScripts();

    void onError(Consumer<ScriptError> handler);

    void onPrint(Consumer<String> handler);

    void run(String chunkName, String source);

    void step(double dt);

    void renderStep(double dt);

    void reloaded();

    void joined(PlayerRef player);

    void leaving(PlayerRef player);

    Map<String, Object> persist();

    void persist(Map<String, Object> data);

    int sleepingTasks();

    @Override
    void close();
}
