package com.moud.plugin.api.world;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.entity.GameObject;
import com.moud.plugin.api.entity.Light;
import com.moud.plugin.api.entity.LightHandleAdapter;
import com.moud.plugin.api.entity.ModelBackedGameObject;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.plugin.api.services.lighting.LightHandle;
import com.moud.plugin.api.services.lighting.PointLightDefinition;
import com.moud.plugin.api.services.model.ModelDefinition;
import com.moud.plugin.api.services.model.ModelHandle;

/**
 * Helps for manipulating time, spawning models, and placing lights.
 */
public final class WorldDsl {
    private final PluginContext context;

    public WorldDsl(PluginContext context) {
        this.context = context;
    }

    public long time() {
        return context.world().getTime();
    }

    public WorldDsl time(long time) {
        context.world().setTime(time);
        return this;
    }

    public int timeRate() {
        return context.world().getTimeRate();
    }

    public WorldDsl timeRate(int rate) {
        context.world().setTimeRate(rate);
        return this;
    }

    public int timeSyncTicks() {
        return context.world().getTimeSynchronizationTicks();
    }

    public WorldDsl timeSyncTicks(int ticks) {
        context.world().setTimeSynchronizationTicks(ticks);
        return this;
    }

    /**
     * Begin building a model instance from a server-side asset path.
     */
    public ModelBuilder spawn(String modelPath) {
        return new ModelBuilder(context, modelPath);
    }

    /**
     * Begin constructing a point light.
     */
    public LightBuilder light() {
        return new LightBuilder(context);
    }

    public static final class ModelBuilder {
        private final PluginContext context;
        private final ModelDefinition.Builder builder;
        private PhysicsController.PhysicsBodyDefinition physics;
        private boolean playerPushEnabled;

        private ModelBuilder(PluginContext context, String modelPath) {
            this.context = context;
            this.builder = ModelDefinition.builder().modelPath(modelPath);
        }

        /**
         * Set the world position where the model should appear.
         */
        public ModelBuilder at(double x, double y, double z) {
            builder.position(new Vector3(x, y, z));
            return this;
        }

        public ModelBuilder at(Vector3 position) {
            builder.position(position);
            return this;
        }

        public ModelBuilder scale(float scale) {
            builder.scale(new Vector3(scale, scale, scale));
            return this;
        }

        public ModelBuilder scale(Vector3 scale) {
            builder.scale(scale);
            return this;
        }

        /**
         * Apply rotation in degrees (pitch, yaw, roll).
         */
        public ModelBuilder rotation(double pitch, double yaw, double roll) {
            builder.rotation(Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll));
            return this;
        }

        public ModelBuilder texture(String texturePath) {
            builder.texture(texturePath);
            return this;
        }

        public ModelBuilder physics(Vector3 halfExtents, float mass, Vector3 initialVelocity) {
            this.physics = new PhysicsController.PhysicsBodyDefinition(halfExtents, mass, initialVelocity, playerPushEnabled);
            return this;
        }

        /**
         * Allow player collisions to impart impulses on this model's physics body.
         */
        public ModelBuilder playerPush(boolean enabled) {
            this.playerPushEnabled = enabled;
            if (this.physics != null) {
                this.physics = new PhysicsController.PhysicsBodyDefinition(
                        physics.halfExtents(),
                        physics.mass(),
                        physics.initialVelocity(),
                        this.playerPushEnabled
                );
            }
            return this;
        }

        /**
         * Spawn the model, optionally attaching a dynamic physics body.
         */
        public GameObject build() {
            ModelHandle handle = context.models().spawn(builder.build());
            if (physics != null && context.physics() != null && context.physics().supported()) {
                context.physics().attachDynamic(handle.id(), physics);
            }
            return new ModelBackedGameObject(context, handle);
        }
    }

    public static final class LightBuilder {
        private final PluginContext context;
        private Vector3 position = Vector3.zero();
        private float r = 1.0f;
        private float g = 1.0f;
        private float b = 1.0f;
        private float brightness = 1.0f;
        private float radius = 8.0f;

        private LightBuilder(PluginContext context) {
            this.context = context;
        }

        /**
         * Configure as a point light (default).
         */
        public LightBuilder point() {
            return this;
        }

        public LightBuilder at(double x, double y, double z) {
            this.position = new Vector3(x, y, z);
            return this;
        }

        public LightBuilder at(Vector3 position) {
            this.position = position;
            return this;
        }

        public LightBuilder color(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
            return this;
        }

        public LightBuilder radius(float radius) {
            this.radius = radius;
            return this;
        }

        public LightBuilder brightness(float brightness) {
            this.brightness = brightness;
            return this;
        }

        /**
         * Create the light in the world and return a handle for later control.
         */
        public Light create() {
            PointLightDefinition definition = new PointLightDefinition(
                    "point",
                    position,
                    null,
                    r, g, b,
                    brightness,
                    radius
            );
            LightHandle handle = context.lighting().create(definition);
            return new LightHandleAdapter(handle);
        }
    }
}
