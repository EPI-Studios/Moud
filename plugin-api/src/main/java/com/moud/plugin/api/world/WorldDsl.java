package com.moud.plugin.api.world;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.entity.Light;
import com.moud.plugin.api.entity.LightHandleAdapter;
import com.moud.plugin.api.models.ModelBuilder;
import com.moud.plugin.api.models.ModelData;
import com.moud.plugin.api.services.lighting.LightHandle;
import com.moud.plugin.api.services.lighting.PointLightDefinition;
import net.minestom.server.coordinate.Pos;

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
     * Begin building a model instance from a model identifier.
     */
    public ModelBuilder spawn(String modelId) {
        ModelData modelData = context.models().getModelData(modelId);
        return new ModelBuilder(context, modelData);
    }

    /**
     * Begin building a model instance from a ModelData object.
     */
    public ModelBuilder spawn(ModelData modelData) {
        return new ModelBuilder(context, modelData);
    }

    /**
     * Begin constructing a point light.
     */
    public LightBuilder light() {
        return new LightBuilder(context);
    }

    /**
     * Convert a Moud Vector3 to a Minestom Pos.
     * @param vector The vector to convert.
     * @return The resulting Pos.
     */
    public Pos toPos(Vector3 vector) {
        return new Pos(vector.x, vector.y, vector.z);
    }

    /**
     * Convert a Minestom Pos to a Moud Vector3.
     * @param pos The Pos to convert.
     * @return The resulting Vector3.
     */
    public Vector3 toVector3(Pos pos) {
        return new Vector3(pos.x(), pos.y(), pos.z());
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
