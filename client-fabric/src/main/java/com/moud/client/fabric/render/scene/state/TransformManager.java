package com.moud.client.fabric.render.scene.state;

import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.render.scene.math.CachedPose;
import com.moud.client.fabric.render.scene.math.NodePoseState;
import com.moud.client.fabric.render.scene.math.Pose;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Quaternionf;

public final class TransformManager {
    private final SceneCacheManager cacheManager;
    private final Map<Long, NodePoseState> poseStatesById = new HashMap<>();
    private final Map<Long, CachedPose> worldPoseCacheById = new HashMap<>();
    private final HashMap<Long, Pose> playerAttachPoseScratch = new HashMap<>();
    private final AtomicLong runtimeOverrideVersion = new AtomicLong();

    private long poseFrameId;
    private float poseFrameTickDelta;

    private String activeAttachmentPlayerUuid;
    private long activeAttachmentRootNodeId;

    private volatile long runtimeBodyNodeId;
    private volatile Pose runtimeBodyWorldPose;
    private volatile float runtimeBodyX;
    private volatile float runtimeBodyY;
    private volatile float runtimeBodyZ;
    private volatile float runtimeBodyYawDeg;

    public TransformManager(SceneCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public AtomicLong runtimeOverrideVersion() {
        return runtimeOverrideVersion;
    }

    public void setRuntimeBodyOverride(long nodeId, float x, float y, float z, float yawDeg) {
        if (nodeId <= 0L) {
            clearRuntimeBodyOverride();
            return;
        }
        float nx = Float.isFinite(x) ? x : 0.0f;
        float ny = Float.isFinite(y) ? y : 0.0f;
        float nz = Float.isFinite(z) ? z : 0.0f;
        float nYaw = Float.isFinite(yawDeg) ? yawDeg : 0.0f;

        if (runtimeBodyNodeId == nodeId
                && Math.abs(runtimeBodyX - nx) < 1e-5f
                && Math.abs(runtimeBodyY - ny) < 1e-5f
                && Math.abs(runtimeBodyZ - nz) < 1e-5f
                && Math.abs(runtimeBodyYawDeg - nYaw) < 1e-4f) {
            return;
        }

        Pose pose = new Pose();
        pose.pos.set(nx, ny, nz);
        pose.rot.set(quatFromEulerDeg(0.0f, nYaw, 0.0f));
        pose.scale.set(1.0f, 1.0f, 1.0f);
        pose.inherit = true;

        runtimeBodyNodeId = nodeId;
        runtimeBodyWorldPose = pose;
        runtimeBodyX = nx;
        runtimeBodyY = ny;
        runtimeBodyZ = nz;
        runtimeBodyYawDeg = nYaw;
        runtimeOverrideVersion.incrementAndGet();
    }

    public void clearRuntimeBodyOverride() {
        if (runtimeBodyNodeId == 0L && runtimeBodyWorldPose == null) {
            return;
        }
        runtimeBodyNodeId = 0L;
        runtimeBodyWorldPose = null;
        runtimeBodyX = 0.0f;
        runtimeBodyY = 0.0f;
        runtimeBodyZ = 0.0f;
        runtimeBodyYawDeg = 0.0f;
        runtimeOverrideVersion.incrementAndGet();
    }

    public void beginPoseFrame(float tickDelta) {
        poseFrameId++;
        EditorContext editorContext = EditorOverlayBus.get();
        poseFrameTickDelta = editorContext != null && editorContext.isActive()
                ? 1.0f
                : NodePropertyUtils.clamp01(tickDelta);
    }

    public void updatePoseStates(boolean shiftPrev) {
        Pose scratch = new Pose();
        for (SceneSnapshot.NodeSnapshot node : cacheManager.cachedNodes()) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            NodePoseState state = poseStatesById.computeIfAbsent(node.nodeId(), ignored -> new NodePoseState());
            parseLocalPoseInto(node, scratch);
            long parentId = node.parentId();

            if (shiftPrev) {
                if (!state.initialized) {
                    Pose.copy(scratch, state.prevLocal);
                } else {
                    Pose.interpolate(state.prevLocal, state.currLocal, poseFrameTickDelta, state.prevLocal);
                }
                Pose.copy(scratch, state.currLocal);
                state.parentId = parentId;
                state.initialized = true;
                state.invalidateInterp();
                continue;
            }

            boolean changed = !state.initialized
                    || state.parentId != parentId
                    || !Pose.approxEquals(state.currLocal, scratch);
            if (changed) {
                if (!state.initialized || state.parentId != parentId) {
                    Pose.copy(scratch, state.prevLocal);
                } else {
                    Pose.copy(state.currLocal, state.prevLocal);
                }
                Pose.copy(scratch, state.currLocal);
                state.parentId = parentId;
                state.initialized = true;
                state.invalidateInterp();
            }
        }

        Iterator<Map.Entry<Long, NodePoseState>> poseIt = poseStatesById.entrySet().iterator();
        while (poseIt.hasNext()) {
            if (!cacheManager.cachedNodesById().containsKey(poseIt.next().getKey())) {
                poseIt.remove();
            }
        }

        Iterator<Map.Entry<Long, CachedPose>> worldIt = worldPoseCacheById.entrySet().iterator();
        while (worldIt.hasNext()) {
            if (!cacheManager.cachedNodesById().containsKey(worldIt.next().getKey())) {
                worldIt.remove();
            }
        }
    }

    public Pose worldPose(long nodeId) {
        if (nodeId <= 0L) {
            return Pose.IDENTITY;
        }
        Pose runtimeBody = runtimeBodyWorldPose;
        if (runtimeBody != null && nodeId == runtimeBodyNodeId) {
            return runtimeBody;
        }

        CachedPose cached = worldPoseCacheById.get(nodeId);
        if (cached != null && cached.frame == poseFrameId) {
            return cached.pose;
        }

        SceneSnapshot.NodeSnapshot node = cacheManager.cachedNodesById().get(nodeId);
        if (node != null && "PlayerAttachment".equals(node.type())) {
            String target = NodePropertyUtils.stringProp(node, "target");
            if (target != null && !target.isBlank() && !"all".equals(target)) {
                return playerAttachmentPose(nodeId, node, target);
            }
            return Pose.IDENTITY;
        }

        NodePoseState state = poseStatesById.get(nodeId);
        if (state == null || !state.initialized) {
            return Pose.IDENTITY;
        }
        Pose local = state.interpolatedLocal(poseFrameId, poseFrameTickDelta);

        if (cached == null) {
            cached = new CachedPose();
            worldPoseCacheById.put(nodeId, cached);
        }
        cached.frame = poseFrameId;
        Pose out = cached.pose;

        if (local.inherit && state.parentId > 0L) {
            SceneSnapshot.NodeSnapshot parent = cacheManager.cachedNodesById().get(state.parentId);
            if (parent != null && "PlayerAttachment".equals(parent.type())) {
                String uuid = NodePropertyUtils.stringProp(parent, "target");
                if (uuid != null && !uuid.isBlank() && !"all".equals(uuid)) {
                    String childAttach = node != null ? NodePropertyUtils.stringProp(node, "attachment_point") : null;
                    String attachPoint = childAttach != null && !childAttach.isBlank()
                            ? childAttach
                            : NodePropertyUtils.stringProp(parent, "attachment_point");
                    float[] attachPos = PlayerBodyAttachmentCache.getAttachPoint(uuid, attachPoint);
                    if (attachPos != null) {
                        float[] root = PlayerBodyAttachmentCache.getRoot(uuid);
                        float yaw = root != null ? root[3] : 0.0f;
                        Pose offset = state.currLocal;
                        float yawRad = (float) Math.toRadians(-yaw);
                        float sinY = (float) Math.sin(yawRad);
                        float cosY = (float) Math.cos(yawRad);
                        float ox = offset.pos.x * cosY - offset.pos.z * sinY;
                        float oz = offset.pos.x * sinY + offset.pos.z * cosY;
                        out.pos.set(attachPos[0] + ox, attachPos[1] + offset.pos.y, attachPos[2] + oz);
                        if (root != null) {
                            out.rot.set(quatFromEulerDeg(0.0f, -yaw, 0.0f)).mul(offset.rot).normalize();
                        } else {
                            out.rot.set(offset.rot);
                        }
                        out.scale.set(offset.scale);
                        out.inherit = false;
                        return out;
                    }
                }
            }
            Pose.compose(worldPose(state.parentId), local, out);
        } else {
            Pose.copy(local, out);
        }
        return out;
    }

    public Pose worldPoseForAttachment(long nodeId, String playerUuid, long attachmentRootNodeId) {
        activeAttachmentPlayerUuid = playerUuid;
        activeAttachmentRootNodeId = attachmentRootNodeId;
        playerAttachPoseScratch.clear();
        SceneSnapshot.NodeSnapshot rootNode = cacheManager.cachedNodesById().get(attachmentRootNodeId);
        if (rootNode != null) {
            computePlayerAttachRootPose(attachmentRootNodeId, rootNode, playerUuid);
        }
        Pose pose = worldPoseForPlayerAttach(nodeId, playerUuid);
        activeAttachmentPlayerUuid = null;
        activeAttachmentRootNodeId = 0L;
        return pose;
    }

    private Pose playerAttachmentPose(long nodeId, SceneSnapshot.NodeSnapshot node, String playerUuid) {
        CachedPose cached = worldPoseCacheById.computeIfAbsent(nodeId, ignored -> new CachedPose());
        cached.frame = poseFrameId;
        Pose out = cached.pose;

        String attachPoint = NodePropertyUtils.stringProp(node, "attachment_point");
        boolean followRot = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "follow_rotation"), false);
        float[] position = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, attachPoint);
        float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);

        if (position != null) {
            out.pos.set(position[0], position[1], position[2]);
            if (followRot && root != null) {
                out.rot.set(quatFromEulerDeg(0.0f, -root[3], 0.0f));
            } else {
                out.rot.identity();
            }
            out.scale.set(1.0f, 1.0f, 1.0f);
            out.inherit = false;
            return out;
        }

        NodePoseState state = poseStatesById.get(nodeId);
        if (state != null && state.initialized) {
            Pose.copy(state.interpolatedLocal(poseFrameId, poseFrameTickDelta), out);
        } else {
            Pose.copy(Pose.IDENTITY, out);
        }
        return out;
    }

    private Pose worldPoseForPlayerAttach(long nodeId, String playerUuid) {
        Pose cached = playerAttachPoseScratch.get(nodeId);
        if (cached != null) {
            return cached;
        }

        SceneSnapshot.NodeSnapshot node = cacheManager.cachedNodesById().get(nodeId);
        if (node != null && "PlayerAttachment".equals(node.type())) {
            return computePlayerAttachRootPose(nodeId, node, playerUuid);
        }

        NodePoseState state = poseStatesById.get(nodeId);
        if (state == null || !state.initialized) {
            return Pose.IDENTITY;
        }
        Pose local = state.currLocal;

        SceneSnapshot.NodeSnapshot root = activeAttachmentRootNodeId > 0L
                ? cacheManager.cachedNodesById().get(activeAttachmentRootNodeId)
                : null;
        boolean followRot = root != null && NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(root, "follow_rotation"), false);
        boolean followAnim = node != null && NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "follow_animation"), false);

        String childAttachPoint = node != null ? NodePropertyUtils.stringProp(node, "attachment_point") : null;
        if (childAttachPoint == null || childAttachPoint.isBlank()) {
            SceneSnapshot.NodeSnapshot parent = state.parentId > 0L ? cacheManager.cachedNodesById().get(state.parentId) : null;
            if (parent != null && "PlayerAttachment".equals(parent.type())) {
                childAttachPoint = NodePropertyUtils.stringProp(parent, "attachment_point");
            }
        }

        if (childAttachPoint != null && !childAttachPoint.isBlank()) {
            float[] position = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, childAttachPoint);
            float[] rootData = PlayerBodyAttachmentCache.getRoot(playerUuid);
            Pose out = new Pose();
            if (position != null) {
                float yaw = rootData != null ? rootData[3] : 0.0f;
                float yawRad = (float) Math.toRadians(-yaw);
                float sinY = (float) Math.sin(yawRad);
                float cosY = (float) Math.cos(yawRad);
                float ox = local.pos.x * cosY - local.pos.z * sinY;
                float oz = local.pos.x * sinY + local.pos.z * cosY;
                out.pos.set(position[0] + ox, position[1] + local.pos.y, position[2] + oz);
                out.rot.set(boneWorldRot(playerUuid, childAttachPoint, local.rot, rootData, followRot, followAnim));
                out.scale.set(local.scale);
                out.inherit = false;
            } else {
                Pose.copy(local, out);
            }
            playerAttachPoseScratch.put(nodeId, out);
            return out;
        }

        Pose out = new Pose();
        if (local.inherit && state.parentId > 0L) {
            Pose parentPose = worldPoseForPlayerAttach(state.parentId, playerUuid);
            if (followAnim) {
                String rootAttachPoint = root != null ? NodePropertyUtils.stringProp(root, "attachment_point") : null;
                float[] rootData = PlayerBodyAttachmentCache.getRoot(playerUuid);
                Quaternionf animatedRot = boneWorldRot(playerUuid, rootAttachPoint, local.rot, rootData, true, true);
                out.pos.set(local.pos).mul(parentPose.scale);
                parentPose.rot.transform(out.pos);
                out.pos.add(parentPose.pos);
                out.rot.set(animatedRot);
                out.scale.set(parentPose.scale).mul(local.scale);
                out.inherit = local.inherit;
            } else {
                Pose.compose(parentPose, local, out);
            }
        } else {
            Pose.copy(local, out);
        }
        playerAttachPoseScratch.put(nodeId, out);
        return out;
    }

    private Pose computePlayerAttachRootPose(long nodeId, SceneSnapshot.NodeSnapshot node, String playerUuid) {
        String attachPoint = NodePropertyUtils.stringProp(node, "attachment_point");
        boolean followRot = NodePropertyUtils.parseBool(NodePropertyUtils.stringProp(node, "follow_rotation"), false);
        float[] position = PlayerBodyAttachmentCache.getAttachPoint(playerUuid, attachPoint);
        float[] root = PlayerBodyAttachmentCache.getRoot(playerUuid);

        Pose out = new Pose();
        if (position != null) {
            out.pos.set(position[0], position[1], position[2]);
            if (followRot && root != null) {
                out.rot.set(quatFromEulerDeg(0.0f, -root[3], 0.0f));
            } else {
                out.rot.identity();
            }
        } else {
            NodePoseState state = poseStatesById.get(nodeId);
            if (state != null && state.initialized) {
                Pose.copy(state.interpolatedLocal(poseFrameId, poseFrameTickDelta), out);
            } else {
                out.rot.identity();
            }
        }
        out.scale.set(1.0f, 1.0f, 1.0f);
        out.inherit = false;
        playerAttachPoseScratch.put(nodeId, out);
        return out;
    }

    private Quaternionf boneWorldRot(String playerUuid, String attachPoint, Quaternionf localRot, float[] root,
                                     boolean followRot, boolean followAnim) {
        Quaternionf result = new Quaternionf();
        if (followAnim) {
            if (root != null) {
                result.set(quatFromEulerDeg(0.0f, -root[3], 0.0f));
            }
            float[] boneRot = PlayerBodyAttachmentCache.getRotation(playerUuid, attachPoint);
            if (boneRot != null) {
                result.mul(quatFromEulerDeg(boneRot[0], boneRot[1], boneRot[2]));
            }
        } else if (followRot && root != null) {
            result.set(quatFromEulerDeg(0.0f, -root[3], 0.0f));
        }
        return result.mul(localRot).normalize();
    }

    private void parseLocalPoseInto(SceneSnapshot.NodeSnapshot node, Pose out) {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        float rxDeg = 0.0f;
        float ryDeg = 0.0f;
        float rzDeg = 0.0f;
        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;
        boolean hasScale = false;
        String inheritRaw = null;

        List<SceneSnapshot.Property> props = node.properties();
        if (props != null) {
            for (SceneSnapshot.Property prop : props) {
                if (prop == null || prop.key() == null) {
                    continue;
                }
                String key = prop.key();
                String value = prop.value();
                switch (key) {
                    case "x" -> x = NodePropertyUtils.parseFloat(value, x);
                    case "y" -> y = NodePropertyUtils.parseFloat(value, y);
                    case "z" -> z = NodePropertyUtils.parseFloat(value, z);
                    case "rx" -> rxDeg = NodePropertyUtils.parseFloat(value, rxDeg);
                    case "ry" -> ryDeg = NodePropertyUtils.parseFloat(value, ryDeg);
                    case "rz" -> rzDeg = NodePropertyUtils.parseFloat(value, rzDeg);
                    case "sx" -> {
                        sx = NodePropertyUtils.parseFloat(value, sx);
                        hasScale = true;
                    }
                    case "sy" -> {
                        sy = NodePropertyUtils.parseFloat(value, sy);
                        hasScale = true;
                    }
                    case "sz" -> {
                        sz = NodePropertyUtils.parseFloat(value, sz);
                        hasScale = true;
                    }
                    case "@inherit_transform" -> inheritRaw = value;
                    default -> {
                    }
                }
            }
        }

        sx = safeScale(sx);
        sy = safeScale(sy);
        sz = safeScale(sz);

        boolean pivotIsMinCorner = "CSGBox".equals(node.type()) || "CSGBlock".equals(node.type());
        float px = x;
        float py = y;
        float pz = z;

        if (hasScale) {
            out.scale.set(sx, sy, sz);
        } else {
            out.scale.set(1.0f, 1.0f, 1.0f);
        }
        if (pivotIsMinCorner && hasScale) {
            px = x + sx * 0.5f;
            py = y + sy * 0.5f;
            pz = z + sz * 0.5f;
        }

        out.pos.set(px, py, pz);
        out.rot.set(quatFromEulerDeg(rxDeg, ryDeg, rzDeg));
        out.inherit = "CSGBlock".equals(node.type()) ? false : shouldInheritTransform(inheritRaw);
    }

    private boolean shouldInheritTransform(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase();
        return !("false".equals(normalized) || "0".equals(normalized));
    }

    private float safeScale(float value) {
        if (!Float.isFinite(value)) {
            return 1.0f;
        }
        float normalized = Math.abs(value) < 1e-6f ? 0.0f : value;
        return Math.max(1e-6f, normalized);
    }

    public static Quaternionf quatFromEulerDeg(float rxDeg, float ryDeg, float rzDeg) {
        float rx = (float) Math.toRadians(Float.isFinite(rxDeg) ? rxDeg : 0.0f);
        float ry = (float) Math.toRadians(Float.isFinite(ryDeg) ? ryDeg : 0.0f);
        float rz = (float) Math.toRadians(Float.isFinite(rzDeg) ? rzDeg : 0.0f);
        return new Quaternionf()
                .rotationZ(rz)
                .mul(new Quaternionf().rotationY(ry))
                .mul(new Quaternionf().rotationX(rx))
                .normalize();
    }
}
