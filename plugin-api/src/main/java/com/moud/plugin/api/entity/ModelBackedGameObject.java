package com.moud.plugin.api.entity;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.PluginContext;
import com.moud.plugin.api.services.ModelService;
import com.moud.plugin.api.services.PhysicsController;
import com.moud.plugin.api.services.model.ModelHandle;

public final class ModelBackedGameObject implements GameObject {
    private final ModelHandle handle;
    private final PhysicsController physics;

    public ModelBackedGameObject(PluginContext context, ModelHandle handle) {
        this.handle = handle;
        this.physics = context.physics();
    }

    @Override
    public long id() {
        return handle.id();
    }

    @Override
    public Vector3 position() {
        return handle.position();
    }

    @Override
    public Quaternion rotation() {
        return handle.rotation();
    }

    @Override
    public GameObject teleport(Vector3 position) {
        handle.setPosition(position);
        return this;
    }

    @Override
    public GameObject teleport(double x, double y, double z) {
        return teleport(new Vector3(x, y, z));
    }

    @Override
    public GameObject rotate(Quaternion rotation) {
        handle.setRotation(rotation);
        return this;
    }

    @Override
    public GameObject scale(Vector3 scale) {
        handle.setScale(scale);
        return this;
    }

    @Override
    public GameObject scale(float uniform) {
        return scale(new Vector3(uniform, uniform, uniform));
    }

    @Override
    public void remove() {
        if (physics != null && physics.supported()) {
            physics.detach(handle.id());
        }
        handle.remove();
    }
}
