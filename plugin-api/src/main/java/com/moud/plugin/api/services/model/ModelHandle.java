package com.moud.plugin.api.services.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.entity.Player;

public interface ModelHandle extends AutoCloseable {
    long id();
    Vector3 position();
    Quaternion rotation();
    Vector3 scale();
    void setPosition(Vector3 position);
    void setRotation(Quaternion rotation);
    void setScale(Vector3 scale);
    void setTexture(String texturePath);
    default void setAnchorToPlayer(Player player, Vector3 localPosition) {
        if (player == null) {
            return;
        }
        setAnchorToEntity(player.uuid().toString(), localPosition);
    }
    default void setAnchorToPlayer(Player player, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                   boolean inheritRotation, boolean inheritScale, boolean includePitch, boolean localSpace) {
        if (player == null) {
            return;
        }
        setAnchorToEntity(player.uuid().toString(), localPosition, localRotation, localScale,
                inheritRotation, inheritScale, includePitch, localSpace);
    }
    default void setAnchorToEntity(String uuid, Vector3 localPosition) {
        setAnchorToEntity(uuid, localPosition, Quaternion.identity(), Vector3.one(), true, true, false, true);
    }
    default void setAnchorToEntity(String uuid, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                   boolean inheritRotation, boolean inheritScale, boolean includePitch, boolean localSpace) {
        throw new UnsupportedOperationException("Anchoring is not supported by this runtime.");
    }
    default void setAnchorToModel(ModelHandle model, Vector3 localPosition) {
        if (model == null) {
            return;
        }
        setAnchorToModel(model.id(), localPosition);
    }
    default void setAnchorToModel(long modelId, Vector3 localPosition) {
        setAnchorToModel(modelId, localPosition, Quaternion.identity(), Vector3.one(), true, true, true);
    }
    default void setAnchorToModel(long modelId, Vector3 localPosition, Quaternion localRotation, Vector3 localScale,
                                  boolean inheritRotation, boolean inheritScale, boolean localSpace) {
        throw new UnsupportedOperationException("Anchoring is not supported by this runtime.");
    }
    default void clearAnchor() {
        throw new UnsupportedOperationException("Anchoring is not supported by this runtime.");
    }
    void remove();

    @Override
    default void close() {
        remove();
    }
}
