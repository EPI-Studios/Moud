package com.moud.server.proxy;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.bridge.AxiomBridgeService;
import com.moud.server.entity.ModelManager;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.InteractionMeta;
import net.minestom.server.instance.Instance;
import org.graalvm.polyglot.HostAccess;

public class ModelProxy {
    private final long id;
    private final Entity entity;
    private final String modelPath;

    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private BoundingBox collisionBox;
    private String texturePath;

    public ModelProxy(Instance instance, String modelPath, Vector3 position, Quaternion rotation, Vector3 scale, String texturePath) {
        this.id = ModelManager.getInstance().nextId();
        this.modelPath = modelPath;
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
        this.texturePath = texturePath != null ? texturePath : "";

        this.entity = new Entity(EntityType.INTERACTION);
        InteractionMeta meta = (InteractionMeta) this.entity.getEntityMeta();
        meta.setResponse(true);
        this.entity.setInstance(instance, new Pos(position.x, position.y, position.z));

        ModelManager.getInstance().register(this);
        broadcastCreate();

        AxiomBridgeService bridge = AxiomBridgeService.getInstance();
        if (bridge != null) {
            bridge.onStaticModelCreated(this);
        }
    }

    private void broadcast(Object packet) {
        ServerNetworkManager networkManager = ServerNetworkManager.getInstance();
        if (networkManager != null) {
            networkManager.broadcast(packet);
        }
    }

    private void broadcastCreate() {
        MoudPackets.S2C_CreateModelPacket packet = new MoudPackets.S2C_CreateModelPacket(
                id, modelPath, position, rotation, scale,
                getCollisionWidth(), getCollisionHeight(), getCollisionDepth(),
                texturePath
        );
        broadcast(packet);
    }

    private void broadcastUpdate(boolean notifyBridge) {
        MoudPackets.S2C_UpdateModelTransformPacket packet = new MoudPackets.S2C_UpdateModelTransformPacket(
                id, position, rotation, scale
        );
        broadcast(packet);

        if (notifyBridge) {
            AxiomBridgeService bridge = AxiomBridgeService.getInstance();
            if (bridge != null) {
                bridge.onStaticModelMoved(this);
            }
        }
    }

    @HostAccess.Export
    public long getId() {
        return id;
    }

    public Entity getEntity() {
        return entity;
    }

    @HostAccess.Export
    public String getModelPath() {
        return modelPath;
    }

    @HostAccess.Export
    public void setPosition(Vector3 position) {
        this.position = position;
        this.entity.teleport(new Pos(position.x, position.y, position.z));
        broadcastUpdate(true);
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
        broadcastUpdate(true);
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        this.rotation = Quaternion.fromEuler((float)pitch, (float)yaw, (float)roll);
        broadcastUpdate(true);
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        return rotation;
    }

    @HostAccess.Export
    public void setScale(Vector3 scale) {
        this.scale = scale;
        broadcastUpdate(true);
    }

    @HostAccess.Export
    public Vector3 getScale() {
        return scale;
    }

    @HostAccess.Export
    public void setTexture(String texturePath) {
        this.texturePath = texturePath != null ? texturePath : "";
        broadcast(new MoudPackets.S2C_UpdateModelTexturePacket(id, this.texturePath));
    }

    @HostAccess.Export
    public String getTexture() {
        return texturePath;
    }

    @HostAccess.Export
    public void setCollisionBox(double width, double height, double depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            this.collisionBox = null;
            this.entity.setBoundingBox(0, 0, 0);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth(0);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight(0);
        } else {
            this.collisionBox = new BoundingBox(width, height, depth);
            this.entity.setBoundingBox(this.collisionBox);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth((float)width);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight((float)height);
        }
        // DO NOT REMOVE OR RECREATE THE ENTITY WHEN YOU WANT TO CHANGE ANY PROPERTIES
        broadcast(new MoudPackets.S2C_UpdateModelCollisionPacket(
                id, getCollisionWidth(), getCollisionHeight(), getCollisionDepth()
        ));
    }

    public double getCollisionWidth() {
        return collisionBox != null ? collisionBox.width() : 0;
    }

    public double getCollisionHeight() {
        return collisionBox != null ? collisionBox.height() : 0;
    }

    public double getCollisionDepth() {
        return collisionBox != null ? collisionBox.depth() : 0;
    }

    @HostAccess.Export
    public void remove() {
        ModelManager.getInstance().unregister(this);
        entity.remove();
        broadcast(new MoudPackets.S2C_RemoveModelPacket(id));

        AxiomBridgeService bridge = AxiomBridgeService.getInstance();
        if (bridge != null) {
            bridge.onStaticModelRemoved(this);
        }
    }


    public void applyBridgeTransform(Vector3 position, Quaternion rotation, Vector3 scale) {
        if (position != null) {
            this.position = position;
            this.entity.teleport(new Pos(position.x, position.y, position.z));
        }
        if (rotation != null) {
            this.rotation = rotation;
        }
        if (scale != null) {
            this.scale = scale;
        }
        broadcastUpdate(false);
    }
}
