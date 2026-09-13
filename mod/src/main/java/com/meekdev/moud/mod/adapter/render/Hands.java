package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Hands {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("moud", "first_person");

    private static final InstanceLayout LAYOUT =
            InstanceLayout.builder().mat4(1).vec4(5).vec2(6).vec4(7).vec2(8).vec2(9).vec4(10)
                    .build();

    private static final float FOV = 70.0f;
    private static final float NEAR = 0.05f;
    private static final float FAR = 100.0f;

    private record Held(Matrix4f transform, Vector4f color, Vector2f light,
                        Vector4f uv, Vector2f box, Vector2f overlay, Vector4f sheet) {}

    private static final List<Held> PACKED = new ArrayList<>();

    private static final Map<Identifier, Identifier> BATCHES = new HashMap<>();

    private Hands() {}

    public static void draw(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.player instanceof AbstractClientPlayer me) || client.level == null) return;
        Character body = ClientScene.own();
        if (body == null || me.isScoping() || me.isInvisible()) return;

        PACKED.clear();
        Identifier skin = me.getSkin().body().texturePath();
        boolean slim = me.getSkin().model() == PlayerModelType.SLIM;

        HumanoidArm arm = me.getMainArm();
        float swing = me.getAttackAnim(partialTick);
        pack(arm, swing, slim, me.isModelPartShown(sleeve(arm)));

        Identifier batch = BATCHES.computeIfAbsent(skin, Hands::register);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(FOV), aspect(client), NEAR, FAR);
        InstanceMeshRegistry.INSTANCE.render(batch,
                new HandContext(client, partialTick, projection));
    }

    private static PlayerModelPart sleeve(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE;
    }

    private static float aspect(Minecraft client) {
        float w = client.getWindow().getWidth();
        float h = client.getWindow().getHeight();
        return h <= 0 ? 1.0f : w / h;
    }

    private static void pack(HumanoidArm arm, float swing, boolean slim, boolean sleeved) {
        float side = arm == HumanoidArm.LEFT ? -1.0f : 1.0f;
        float root = (float) Math.sqrt(swing);
        float px = -0.3f * (float) Math.sin(root * Math.PI);
        float py = 0.4f * (float) Math.sin(root * Math.PI * 2);
        float pz = -0.4f * (float) Math.sin(swing * Math.PI);
        float turn = (float) Math.sin(swing * swing * Math.PI);
        float reach = (float) Math.sin(root * Math.PI);

        Matrix4f m = new Matrix4f();
        m.translate(side * (px + 0.64000005f), py - 0.6f, pz - 0.71999997f);
        m.rotateY((float) Math.toRadians(side * 45.0));
        m.rotateY((float) Math.toRadians(side * reach * 70.0));
        m.rotateZ((float) Math.toRadians(side * turn * -20.0));
        m.translate(side * -1.0f, 3.6f, 3.5f);
        m.rotateZ((float) Math.toRadians(side * 120.0));
        m.rotateX((float) Math.toRadians(200.0));
        m.rotateY((float) Math.toRadians(side * -135.0));
        m.translate(side * 5.6f, 0.0f, 0.0f);

        m.translate(side * -0.3125f, 0.125f, 0.0f);
        m.rotateZ(side * 0.1f);

        m.rotateZ((float) Math.PI);

        boolean left = arm == HumanoidArm.LEFT;
        float wide = slim ? 3f : 4f;
        float minX = left ? -1f : (slim ? -2f : -3f);
        m.translate((minX + wide / 2f) / 16f, (-2f + 6f) / 16f, 0f);

        emit(m, arm, wide, 0f);
        if (sleeved) emit(m, arm, wide, 0.25f);
    }

    private static void emit(Matrix4f frame, HumanoidArm arm, float wide, float grow) {
        boolean left = arm == HumanoidArm.LEFT;
        boolean sleeve = grow > 0;
        float u = left ? (sleeve ? 48 : 32) : 40;
        float v = left ? 48 : (sleeve ? 32 : 16);

        float out = grow * 2;
        Matrix4f box = new Matrix4f(frame).scale(
                (wide + out) / 16f, (12 + out) / 16f, (4 + out) / 16f);
        PACKED.add(new Held(box,
                new Vector4f(1, 1, 1, 1),
                new Vector2f(240, 240),
                new Vector4f(u, v, wide, 12),
                new Vector2f(4, grow > 0 ? 1f : 0f),
                new Vector2f(0, 0),
                new Vector4f(64, 64, 0, 0)));
    }

    private static Identifier register(Identifier skin) {
        Identifier id = Identifier.fromNamespaceAndPath("moud",
                ID.getPath() + "_" + skin.getNamespace() + "_" + skin.getPath().replace('/', '_'));
        InstancedMesh.<Held>builder(LAYOUT,
                        (inst, p) -> p.putMat4(inst.transform()).putVec4(inst.color())
                                .putVec2(inst.light().x, inst.light().y)
                                .putVec4(inst.uv())
                                .putVec2(inst.box().x, inst.box().y)
                                .putVec2(inst.overlay().x, inst.overlay().y)
                                .putVec4(inst.sheet()))
                .shader(Identifier.fromNamespaceAndPath("moud", "instance/skin"))
                .texture(skin)
                .extraSampler("LightMap", Parts::lightMap, 1)
                .geometry(MeshData.unitCube())
                .renderState(RenderState.builder()
                        .blend(RenderState.BlendMode.ALPHA)
                        .build())
                .phase(InstancePhase.WORLD_LAST)
                .manual()
                .onRender((ctx, batch) -> {
                    for (Held one : PACKED) batch.add(one);
                })
                .register(id);
        return id;
    }

    private static final class HandContext extends InstanceRenderContext {

        private final Minecraft client;
        private final float deltaTick;
        private final Matrix4f projection;
        private static final Matrix4f VIEW = new Matrix4f();

        HandContext(Minecraft client, float deltaTick, Matrix4f projection) {
            this.client = client;
            this.deltaTick = deltaTick;
            this.projection = projection;
        }

        @Override
        public Minecraft client() {
            return client;
        }

        @Override
        public ClientLevel world() {
            return client.level;
        }

        @Override
        public float deltaTick() {
            return deltaTick;
        }

        @Override
        public Vec3 cameraPos() {
            return Vec3.ZERO;
        }

        @Override
        public Matrix4fc viewMatrix() {
            return VIEW;
        }

        @Override
        public Matrix4fc projectionMatrix() {
            return projection;
        }
    }
}
