package com.moud.server.physics.player;

import com.moud.api.physics.player.PlayerPhysicsControllers;
import com.moud.server.logging.MoudLogger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public final class SharedPhysicsLoader {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(SharedPhysicsLoader.class);

    private final Context jsContext;
    private final PhysicsScriptBinding physicsBinding;

    public SharedPhysicsLoader(Context jsContext) {
        this.jsContext = jsContext;
        this.physicsBinding = new PhysicsScriptBinding();
    }


    public boolean loadSharedPhysics(String scriptSource) {
        if (scriptSource == null || scriptSource.isBlank()) {
            return false;
        }

        try {
            jsContext.enter();

            jsContext.getBindings("js").putMember("Physics", physicsBinding);

            String wrappedSource = """
                (function() {
                    const exports = {};
                    const module = { exports: exports };
                    %s
                    return module.exports.controller || module.exports.default || module.exports
                        || exports.controller || exports.default;
                })()
                """.formatted(scriptSource);

            Source source = Source.newBuilder("js", wrappedSource, "shared-physics.js").build();
            Value result = jsContext.eval(source);

            if (result == null || result.isNull()) {
                LOGGER.debug("Shared physics script did not export a controller");
                return false;
            }

            return registerController(result);
        } catch (Exception e) {
            LOGGER.error("Failed to load shared physics script: {}", e.getMessage(), e);
            return false;
        } finally {
            jsContext.leave();
        }
    }

    private boolean registerController(Value controllerValue) {
        if (!controllerValue.hasMembers()) {
            LOGGER.warn("Shared physics export is not a valid controller object");
            return false;
        }

        Value idValue = controllerValue.getMember("id");
        Value stepValue = controllerValue.getMember("step");

        if (idValue == null || idValue.isNull() || !idValue.isString()) {
            LOGGER.warn("Shared physics controller missing 'id' property");
            return false;
        }

        if (stepValue == null || stepValue.isNull() || !stepValue.canExecute()) {
            LOGGER.warn("Shared physics controller missing 'step' function");
            return false;
        }

        String id = idValue.asString();
        ScriptedPhysicsController controller = new ScriptedPhysicsController(id, stepValue, jsContext);

        PlayerPhysicsControllers.register(id, controller);
        LOGGER.info("Registered shared physics controller: {}", id);

        return true;
    }

    public PhysicsScriptBinding getPhysicsBinding() {
        return physicsBinding;
    }
}
