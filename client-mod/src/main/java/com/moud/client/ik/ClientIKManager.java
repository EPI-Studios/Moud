package com.moud.client.ik;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientIKManager {
    private static final ClientIKManager INSTANCE = new ClientIKManager();
    private static final boolean DEBUG_LINES_ENABLED =
            Boolean.parseBoolean(System.getProperty("moud.ik.debugLines", "false"));
    private final Map<String, ClientIKChain> chains = new ConcurrentHashMap<>();

    public static ClientIKManager getInstance() {
        return INSTANCE;
    }

    public void handleCreate(MoudPackets.S2C_IKCreateChainPacket packet) {
        chains.put(packet.chainId(), new ClientIKChain(packet));
    }

    public void handleUpdate(MoudPackets.S2C_IKUpdateChainPacket packet) {
        ClientIKChain chain = chains.get(packet.chainId());
        if (chain != null) {
            chain.update(packet);
        }
    }

    public void handleBatchUpdate(MoudPackets.S2C_IKBatchUpdatePacket packet) {
        if (packet.chains() == null) return;
        for (MoudPackets.IKChainBatchEntry entry : packet.chains()) {
            ClientIKChain chain = chains.get(entry.chainId());
            if (chain != null) {
                chain.update(entry);
            }
        }
    }

    public void handleUpdateTarget(MoudPackets.S2C_IKUpdateTargetPacket packet) {
        ClientIKChain chain = chains.get(packet.chainId());
        if (chain != null) {
            chain.updateTarget(packet);
        }
    }

    public void handleUpdateRoot(MoudPackets.S2C_IKUpdateRootPacket packet) {
        ClientIKChain chain = chains.get(packet.chainId());
        if (chain != null) {
            chain.updateRoot(packet);
        }
    }

    public void handleAttach(MoudPackets.S2C_IKAttachPacket packet) {
        ClientIKChain chain = chains.get(packet.chainId());
        if (chain != null) {
            chain.attach(packet);
        }
    }

    public void handleDetach(MoudPackets.S2C_IKDetachPacket packet) {
        ClientIKChain chain = chains.get(packet.chainId());
        if (chain != null) {
            chain.detach();
        }
    }

    public void handleRemove(MoudPackets.S2C_IKRemoveChainPacket packet) {
        chains.remove(packet.chainId());
    }

    public void clear() {
        chains.clear();
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cameraPos) {
        if (!DEBUG_LINES_ENABLED) return;
        if (chains.isEmpty()) return;

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (ClientIKChain chain : chains.values()) {
            List<MoudPackets.IKJointData> joints = chain.getJoints();
            if (joints == null || joints.isEmpty()) continue;

            for (int i = 0; i < joints.size() - 1; i++) {
                Vector3 p1 = joints.get(i).position();
                Vector3 p2 = joints.get(i + 1).position();

                drawLine(buffer, matrix,
                        p1.x, p1.y, p1.z,
                        p2.x, p2.y, p2.z,
                        1f, 1f, 0f, 1f);
            }

            Vector3 target = chain.getTargetPosition();
            if (target != null && !joints.isEmpty()) {
                Vector3 end = joints.get(joints.size() - 1).position();
                drawLine(buffer, matrix,
                        end.x, end.y, end.z,
                        target.x, target.y, target.z,
                        1f, 0f, 0f, 0.5f);
            }
        }

        matrices.pop();
    }

    private void drawLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }
}
