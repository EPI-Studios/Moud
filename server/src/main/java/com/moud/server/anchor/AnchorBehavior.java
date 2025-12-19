package com.moud.server.anchor;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets.DisplayAnchorType;
import com.moud.server.entity.ModelManager;
import com.moud.server.proxy.ModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class AnchorBehavior {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnchorBehavior.class);

    private DisplayAnchorType anchorType = DisplayAnchorType.FREE;
    private UUID anchorEntityUuid;
    private WeakReference<Entity> anchorEntityRef;
    private Long anchorModelId;
    private Integer anchorBlockX;
    private Integer anchorBlockY;
    private Integer anchorBlockZ;

    private Vector3 localPosition = Vector3.zero();
    private Quaternion localRotation = Quaternion.identity();
    private Vector3 localScale = Vector3.one();

    private boolean localSpace = true;
    private boolean inheritRotation = true;
    private boolean inheritScale = true;
    private boolean includePitch = false;

    private AnchorChangeListener changeListener;

    @FunctionalInterface
    public interface AnchorChangeListener {
        void onAnchorChanged(AnchorBehavior anchor);
    }

    public AnchorBehavior() {
    }

    public AnchorBehavior(AnchorChangeListener listener) {
        this.changeListener = listener;
    }

    public void setChangeListener(AnchorChangeListener listener) {
        this.changeListener = listener;
    }


    public DisplayAnchorType getAnchorType() {
        return anchorType;
    }

    public UUID getAnchorEntityUuid() {
        return anchorEntityUuid;
    }

    public Long getAnchorModelId() {
        return anchorModelId;
    }

    public Integer getAnchorBlockX() {
        return anchorBlockX;
    }

    public Integer getAnchorBlockY() {
        return anchorBlockY;
    }

    public Integer getAnchorBlockZ() {
        return anchorBlockZ;
    }

    public Vector3 getLocalPosition() {
        return localPosition != null ? new Vector3(localPosition) : Vector3.zero();
    }

    public Quaternion getLocalRotation() {
        return localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
    }

    public Vector3 getLocalScale() {
        return localScale != null ? new Vector3(localScale) : Vector3.one();
    }

    public boolean isLocalSpace() {
        return localSpace;
    }

    public boolean isInheritRotation() {
        return inheritRotation;
    }

    public boolean isInheritScale() {
        return inheritScale;
    }

    public boolean isIncludePitch() {
        return includePitch;
    }

    public boolean isAnchored() {
        return anchorType != DisplayAnchorType.FREE;
    }

    public void anchorToBlock(int x, int y, int z, Vector3 localPosition) {
        anchorToBlock(x, y, z, localPosition, Quaternion.identity(), Vector3.one(), true, true, true);
    }

    public void anchorToBlock(int x, int y, int z, Vector3 localPosition, Quaternion localRotation,
                              Vector3 localScale, boolean inheritRotation, boolean inheritScale, boolean localSpace) {
        this.anchorType = DisplayAnchorType.BLOCK;
        this.anchorEntityUuid = null;
        this.anchorEntityRef = null;
        this.anchorModelId = null;
        this.anchorBlockX = x;
        this.anchorBlockY = y;
        this.anchorBlockZ = z;
        this.localPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        this.localRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        this.localScale = localScale != null ? new Vector3(localScale) : Vector3.one();
        this.localSpace = localSpace;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
        this.includePitch = false;
        notifyChange();
    }

    public void anchorToEntity(UUID entityUuid, Vector3 localPosition) {
        anchorToEntity(entityUuid, localPosition, Quaternion.identity(), Vector3.one(), true, true, false, true);
    }

    public void anchorToEntity(UUID entityUuid, Vector3 localPosition, Quaternion localRotation,
                               Vector3 localScale, boolean inheritRotation, boolean inheritScale,
                               boolean includePitch, boolean localSpace) {
        if (entityUuid == null) {
            LOGGER.warn("Attempted to anchor to null entity UUID");
            return;
        }
        this.anchorType = DisplayAnchorType.ENTITY;
        this.anchorEntityUuid = entityUuid;
        this.anchorEntityRef = null; // resolved lazily
        this.anchorModelId = null;
        this.anchorBlockX = null;
        this.anchorBlockY = null;
        this.anchorBlockZ = null;
        this.localPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        this.localRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        this.localScale = localScale != null ? new Vector3(localScale) : Vector3.one();
        this.localSpace = localSpace;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
        this.includePitch = includePitch;
        notifyChange();
    }

    public void anchorToModel(long modelId, Vector3 localPosition) {
        anchorToModel(modelId, localPosition, Quaternion.identity(), Vector3.one(), true, true, true);
    }

    public void anchorToModel(long modelId, Vector3 localPosition, Quaternion localRotation,
                              Vector3 localScale, boolean inheritRotation, boolean inheritScale, boolean localSpace) {
        this.anchorType = DisplayAnchorType.MODEL;
        this.anchorEntityUuid = null;
        this.anchorEntityRef = null;
        this.anchorModelId = modelId;
        this.anchorBlockX = null;
        this.anchorBlockY = null;
        this.anchorBlockZ = null;
        this.localPosition = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        this.localRotation = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        this.localScale = localScale != null ? new Vector3(localScale) : Vector3.one();
        this.localSpace = localSpace;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
        this.includePitch = false;
        notifyChange();
    }

    public void clear() {
        this.anchorType = DisplayAnchorType.FREE;
        this.anchorEntityUuid = null;
        this.anchorEntityRef = null;
        this.anchorModelId = null;
        this.anchorBlockX = null;
        this.anchorBlockY = null;
        this.anchorBlockZ = null;
        this.localPosition = Vector3.zero();
        this.localRotation = Quaternion.identity();
        this.localScale = Vector3.one();
        this.localSpace = true;
        this.inheritRotation = true;
        this.inheritScale = true;
        this.includePitch = false;
        notifyChange();
    }

    public boolean updateTracking(Transformable target) {
        if (anchorType == DisplayAnchorType.FREE) {
            return false;
        }

        ParentTransform parent = resolveParent();
        if (parent == null) {
            clear();
            return false;
        }

        Vector3 localPos = localPosition != null ? new Vector3(localPosition) : Vector3.zero();
        Quaternion localRot = localRotation != null ? new Quaternion(localRotation) : Quaternion.identity();
        Vector3 localScl = localScale != null ? new Vector3(localScale) : Vector3.one();

        Vector3 translated = localPos;
        if (localSpace) {
            translated = localPos.multiply(parent.scale());
            translated = parent.rotation().rotate(translated);
        }

        Vector3 worldPos = parent.position().add(translated);
        Quaternion worldRot = inheritRotation ? parent.rotation().multiply(localRot).normalize() : localRot;
        Vector3 worldScale = inheritScale ? parent.scale().multiply(localScl) : localScl;

        target.applyAnchoredTransform(worldPos, worldRot, worldScale);
        return true;
    }

    private ParentTransform resolveParent() {
        return switch (anchorType) {
            case BLOCK -> resolveBlockParent();
            case ENTITY -> resolveEntityParent();
            case MODEL -> resolveModelParent();
            case FREE -> null;
        };
    }

    private ParentTransform resolveBlockParent() {
        if (anchorBlockX == null || anchorBlockY == null || anchorBlockZ == null) {
            return null;
        }
        Vector3 pos = new Vector3(anchorBlockX + 0.5f, anchorBlockY + 0.5f, anchorBlockZ + 0.5f);
        return new ParentTransform(pos, Quaternion.identity(), Vector3.one());
    }

    private ParentTransform resolveEntityParent() {
        Entity entity = anchorEntityRef != null ? anchorEntityRef.get() : null;
        if (entity == null && anchorEntityUuid != null) {
            entity = resolveEntity(anchorEntityUuid);
            if (entity != null) {
                anchorEntityRef = new WeakReference<>(entity);
            }
        }
        if (entity == null) {
            return null;
        }

        Pos pos = entity.getPosition();
        float pitch = includePitch ? pos.pitch() : 0.0f;
        Quaternion rot = Quaternion.fromEuler(pitch, pos.yaw(), 0.0f);
        return new ParentTransform(new Vector3(pos.x(), pos.y(), pos.z()), rot, Vector3.one());
    }

    private ParentTransform resolveModelParent() {
        if (anchorModelId == null) {
            return null;
        }
        ModelProxy parent = ModelManager.getInstance().getById(anchorModelId);
        if (parent == null) {
            return null;
        }
        Vector3 parentPos = parent.getPosition() != null ? new Vector3(parent.getPosition()) : Vector3.zero();
        Quaternion parentRot = parent.getRotation() != null ? new Quaternion(parent.getRotation()) : Quaternion.identity();
        Vector3 parentScale = parent.getScale() != null ? new Vector3(parent.getScale()) : Vector3.one();
        return new ParentTransform(parentPos, parentRot, parentScale);
    }

    private Entity resolveEntity(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        // online players first
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (player != null) {
            return player;
        }
        // then model entities
        ModelProxy modelProxy = ModelManager.getInstance().getByEntityUuid(uuid);
        if (modelProxy != null) {
            return modelProxy.getEntity();
        }
        return null;
    }

    private void notifyChange() {
        if (changeListener != null) {
            changeListener.onAnchorChanged(this);
        }
    }

    private record ParentTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
    }
}
