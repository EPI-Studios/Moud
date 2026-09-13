package com.meekdev.moud.script.engine;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.script.api.AudioRef;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.ModuleSource;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.PostRef;
import com.meekdev.moud.script.err.ScriptError;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

// one running place, whatever it is written in
//
// this is not an abstraction invented ahead of a need: it is the surface a place already used, cut
// out along the line it already had. everything the engine asks of a place is here and nothing
// else is, so a second language has an exact contract to satisfy rather than a class to imitate
public interface ScriptEngine extends AutoCloseable {

    // the tree and the classes it may name. called once, before anything runs
    void bind(Instance world, ClassRegistry classes);

    // what carries a delivery off this side, and which side it is. a channel's two directions are not
    // symmetric, so each side has to know which of the two verbs is its own
    void bindPost(PostRef post, boolean client);

    // the client's own three: the camera it draws through, the input it reads, and the body it
    // drives. a server side place never gets these
    void bindClient(Instance camera, CameraRef lens, InputRef input, Supplier<Instance> own);

    // where a script's failure goes. a broken edit must not take the session with it
    void bindModules(ModuleSource source);

    void bindAudio(AudioRef audio);

    void bindBlocks(BlockRef blocks);

    void onError(Consumer<ScriptError> handler);

    void run(String chunkName, String source);

    void step(double dt);

    void renderStep(double dt);

    // the place was rebuilt from source. whatever it carried across is already back
    void reloaded();

    void joined(PlayerRef player);

    void leaving(PlayerRef player);

    // what survives a reload, as data rather than as anything the language owns
    Map<String, Object> persist();

    void persist(Map<String, Object> data);

    // what the overlay reports. a number rather than a scheduler, because a scheduler is the
    // language's own business and nothing outside it should hold one
    int sleepingTasks();

    @Override
    void close();
}
