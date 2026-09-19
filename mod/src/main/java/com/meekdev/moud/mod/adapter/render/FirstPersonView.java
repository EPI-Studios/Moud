package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.character.Appearance;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.character.ViewModel;
import com.meekdev.moud.core.character.ViewModels;
import com.meekdev.moud.core.instance.Bone;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.PlaceFiles;
import com.meekdev.moud.mod.place.Output;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class FirstPersonView {

    private static final float NEAR = 0.05f;
    private static final float FAR = 100f;

    private static @Nullable ViewModel current;
    private static CFrame offset = CFrame.IDENTITY;

    private static @Nullable ModelPart wide;
    private static @Nullable ModelPart slim;
    private static @Nullable ProjectionMatrixBuffer lens;
    private static final Projection PROJECTION = new Projection();
    private static final ItemStackRenderState[] HELD = {new ItemStackRenderState(), new ItemStackRenderState()};

    private FirstPersonView() {}

    public static void install() {
        ViewModels.files(res -> {
            byte[] bytes = PlaceFiles.read(res);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }, note -> Output.add(Output.Level.WARN, "view model", note));
    }

    public static void begin(double dt) {
        current = null;
        offset = CFrame.IDENTITY;
        if (EditMode.editing()) return;
        ViewModel view = ViewModels.of(ClientScene.own());
        if (view == null) return;
        ViewModels.animate(view, dt);
        current = view;
    }

    public static void end(Camera camera, double dt) {
        ViewModel view = current;
        if (view == null || !view.isAlive()) {
            current = null;
            return;
        }
        Instance holder = view.parent();
        view.cframe = holder == null ? camera.cframe : Transforms.world(holder).inverse().mul(camera.cframe);
        ViewModels.solve(view, dt);
        offset = Skins.inside() ? ViewModels.offset(view) : CFrame.IDENTITY;
    }

    public static CFrame cameraOffset() {
        ViewModel view = current;
        return view == null || !view.isAlive() ? CFrame.IDENTITY : offset;
    }

    public static boolean draw(PoseStack poses, SubmitNodeCollector out, LocalPlayer player, int light) {
        ViewModel view = current;
        Character body = ClientScene.own();
        if (view == null || !view.isAlive() || body == null || view.parent() != body || !ViewModels.active(view)) return false;
        Minecraft client = Minecraft.getInstance();
        boolean own = view.fieldOfView > 0;
        if (own) {
            if (lens == null) lens = new ProjectionMatrixBuffer("moud view model");
            PROJECTION.setupPerspective(NEAR, FAR, (float) view.fieldOfView,
                    client.getWindow().getWidth(), client.getWindow().getHeight());
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(lens.getBuffer(PROJECTION), ProjectionType.PERSPECTIVE);
        }
        Map<String, Bone> joints = ViewModels.joints(view);
        if (ViewModels.model(view) == null) arms(view, joints, body, player, poses, out, light);
        else ViewModelMeshes.draw(view, joints, poses, out, light);
        items(view, joints, body, player, poses, out, light);
        client.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
        client.renderBuffers().bufferSource().endBatch();
        if (own) RenderSystem.restoreProjectionMatrix();
        return true;
    }

    private static void arms(ViewModel view, Map<String, Bone> joints, Character body, LocalPlayer player,
                             PoseStack poses, SubmitNodeCollector out, int light) {
        Appearance look = Rig.appearance(body);
        Identifier skin = look == null ? player.getSkin().body().texturePath() : Skins.bodySheet(look, player);
        ModelPart root = look != null && look.slim ? slim() : wide();
        for (boolean right : new boolean[] {true, false}) {
            Bone arm = joints.get(right ? ViewModels.RIGHT_ARM : ViewModels.LEFT_ARM);
            if (arm == null) continue;
            ModelPart part = root.getChild(right ? "right_arm" : "left_arm");
            part.resetPose();
            part.setPos(0, 0, 0);
            part.visible = true;
            part.getChild(right ? "right_sleeve" : "left_sleeve").visible =
                    player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
            poses.pushPose();
            place(poses, ViewModels.inView(view, arm), arm.scale);
            poses.mulPose(new Quaternionf().rotationZ((float) Math.PI));
            out.submitModelPart(part, poses, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, null);
            poses.popPose();
        }
    }

    private static void items(ViewModel view, Map<String, Bone> joints, Character body, LocalPlayer player,
                              PoseStack poses, SubmitNodeCollector out, int light) {
        Minecraft client = Minecraft.getInstance();
        for (boolean right : new boolean[] {true, false}) {
            Bone grip = joints.get(right ? ViewModels.RIGHT_ITEM : ViewModels.LEFT_ITEM);
            if (grip == null) continue;
            String forced = right ? body.rightItemOverride : body.leftItemOverride;
            String id = !forced.isEmpty() ? forced : right ? body.rightItem : body.leftItem;
            if (id.isEmpty()) continue;
            ItemStack stack = HeldItems.stack(player, !right, id);
            if (stack.isEmpty()) continue;
            ItemStackRenderState state = HELD[right ? 0 : 1];
            client.getItemModelResolver().updateForLiving(state, stack,
                    right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, player);
            if (state.isEmpty()) continue;
            poses.pushPose();
            place(poses, ViewModels.inView(view, grip), grip.scale);
            poses.mulPose(new Quaternionf().rotationZ((float) Math.PI));
            state.submit(poses, out, light, OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
        }
    }

    static void place(PoseStack poses, CFrame frame, Vector3 scale) {
        Vector3 at = frame.position();
        Quat turn = frame.rotation();
        poses.translate((float) at.x(), (float) at.y(), (float) at.z());
        poses.mulPose(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()));
        if (!scale.equals(Vector3.ONE)) poses.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }

    private static ModelPart wide() {
        if (wide == null) wide = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        return wide;
    }

    private static ModelPart slim() {
        if (slim == null) slim = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot();
        return slim;
    }
}
