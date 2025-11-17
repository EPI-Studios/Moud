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

public final class WorldDsl {
    private final PluginContext context;

    public WorldDsl(PluginContext context) {
        this.context = context;
    }

    public ModelBuilder spawn(String modelPath) {
        return new ModelBuilder(context, modelPath);
    }

    public LightBuilder light() {
        return new LightBuilder(context);
    }

    public static final class ModelBuilder {
        private final PluginContext context;
        private final ModelDefinition.Builder builder;
        private PhysicsController.PhysicsBodyDefinition physics;

        private ModelBuilder(PluginContext context, String modelPath) {
            this.context = context;
            this.builder = ModelDefinition.builder().modelPath(modelPath);
        }

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

        public ModelBuilder rotation(double pitch, double yaw, double roll) {
            builder.rotation(Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll));
            return this;
        }

        public ModelBuilder texture(String texturePath) {
            builder.texture(texturePath);
            return this;
        }

        public ModelBuilder physics(Vector3 halfExtents, float mass, Vector3 initialVelocity) {
            this.physics = new PhysicsController.PhysicsBodyDefinition(halfExtents, mass, initialVelocity);
            return this;
        }

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
