package com.moud.plugin.api.models;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.entity.GameObject;
import com.moud.plugin.api.entity.ModelBackedGameObject;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.plugin.api.services.model.ModelDefinition;
import com.moud.plugin.api.services.model.ModelHandle;

public final class ModelBuilder {
    private final PluginContext context;
    private final ModelDefinition.Builder builder;
    private PhysicsController.PhysicsBodyDefinition physics;
    private boolean playerPushEnabled;

    public ModelBuilder(PluginContext context, String modelPath) {
        this.context = context;
        this.builder = ModelDefinition.builder().modelPath(modelPath);
    }

    public ModelBuilder(PluginContext context, ModelData modelData) {
        this.context = context;
        this.builder = ModelDefinition.builder()
                .modelData(modelData);
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