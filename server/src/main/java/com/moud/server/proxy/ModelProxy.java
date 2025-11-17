package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.collision.OBB;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.collision.MinestomCollisionAdapter;
import com.moud.server.entity.ModelManager;
import com.moud.server.physics.PhysicsService;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.InteractionMeta;
import net.minestom.server.instance.Instance;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;

@TsExpose
public class ModelProxy {
    private final long id;
    private final Entity entity;
    private final String modelPath;

    private Vector3 position;
    private Quaternion rotation;
    private Vector3 scale;
    private BoundingBox collisionBox;
    private String texturePath;
    private List<OBB> collisionBoxes = new ArrayList<>();

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

        generateAccurateCollision();

        ModelManager.getInstance().register(this);
        broadcastCreate();

    }

    private void generateAccurateCollision() {
        List<OBB> boxes = com.moud.server.physics.mesh.ModelCollisionLibrary.getCollisionBoxes(modelPath);
        if (boxes != null && !boxes.isEmpty()) {
            setCollisionBoxes(boxes);
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

    private void broadcastUpdate() {
        MoudPackets.S2C_UpdateModelTransformPacket packet = new MoudPackets.S2C_UpdateModelTransformPacket(
                id, position, rotation, scale
        );
        broadcast(packet);
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
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, position, null);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, null, rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void setRotationFromEuler(double pitch, double yaw, double roll) {
        this.rotation = Quaternion.fromEuler((float)pitch, (float)yaw, (float)roll);
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.handleModelManualTransform(this, null, this.rotation);
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        return rotation;
    }

    @HostAccess.Export
    public void setScale(Vector3 scale) {
        this.scale = scale;
        broadcastUpdate();
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
            this.collisionBoxes.clear();
            this.entity.setBoundingBox(0, 0, 0);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth(0);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight(0);
        } else {
            this.collisionBox = new BoundingBox(width, height, depth);
            this.entity.setBoundingBox(this.collisionBox);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth((float)width);
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight((float)height);
        }
        broadcast(new MoudPackets.S2C_UpdateModelCollisionPacket(
                id, getCollisionWidth(), getCollisionHeight(), getCollisionDepth()
        ));
    }

    public void setCollisionBoxes(List<OBB> boxes) {
        this.collisionBoxes = new ArrayList<>(boxes);
        List<BoundingBox> minestomBoxes = MinestomCollisionAdapter.convertToBoundingBoxes(
            boxes, position, rotation, scale
        );
        if (!minestomBoxes.isEmpty()) {
            BoundingBox mainBox = MinestomCollisionAdapter.getLargestBox(minestomBoxes);
            this.collisionBox = mainBox;
            this.entity.setBoundingBox(mainBox);
            ((InteractionMeta)this.entity.getEntityMeta()).setWidth((float)mainBox.width());
            ((InteractionMeta)this.entity.getEntityMeta()).setHeight((float)mainBox.height());
        }
        broadcastCollisionBoxes();
    }

    private void broadcastCollisionBoxes() {
        if (collisionBoxes.isEmpty()) {
            return;
        }
        List<MoudPackets.CollisionBoxData> boxData = new ArrayList<>();
        for (OBB obb : collisionBoxes) {
            boxData.add(new MoudPackets.CollisionBoxData(obb.center, obb.halfExtents, obb.rotation));
        }
        broadcast(new MoudPackets.S2C_SyncModelCollisionBoxesPacket(id, boxData));
    }

    public List<OBB> getCollisionBoxes() {
        return new ArrayList<>(collisionBoxes);
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

    public void syncPhysicsTransform(Vector3 position, Quaternion rotation) {
        if (position != null) {
            this.position = position;
            this.entity.teleport(new Pos(position.x, position.y, position.z));
        }
        if (rotation != null) {
            this.rotation = rotation;
        }
        broadcastUpdate();
    }

    @HostAccess.Export
    public void remove() {
        PhysicsService physics = PhysicsService.getInstance();
        if (physics != null) {
            physics.detachModel(this);
        }
        ModelManager.getInstance().unregister(this);
        entity.remove();
        broadcast(new MoudPackets.S2C_RemoveModelPacket(id));

    }


}
