package com.moud.client.fabric.editor.state;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.editor.dialogs.CreateNodeDialog;
import com.moud.client.fabric.editor.net.EditorNet;
import com.moud.client.fabric.editor.tools.EditorTool;

import com.miry.graphics.Texture;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;

public final class EditorRuntime {
    private final EditorState state;
    private final EditorNet net;
    private CreateNodeDialog createNodeDialog;
    private AssetsClient assets;
    private Session session;
    private Texture viewportTexture;
    private EditorTool tool = EditorTool.MOVE;
    private float framebufferScaleX = 1.0f;
    private float framebufferScaleY = 1.0f;
    private boolean rightDown;
    private boolean rightPressed;
    private boolean rightReleased;

    public EditorRuntime(EditorState state, EditorNet net) {
        this.state = state;
        this.net = net;
    }

    public EditorState state() {
        return state;
    }

    public EditorNet net() {
        return net;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Texture viewportTexture() {
        return viewportTexture;
    }

    public void setViewportTexture(Texture viewportTexture) {
        this.viewportTexture = viewportTexture;
    }

    public EditorTool tool() {
        return tool;
    }

    public void setTool(EditorTool tool) {
        this.tool = tool == null ? EditorTool.SELECT : tool;
    }

    public float framebufferScaleX() {
        return framebufferScaleX;
    }

    public float framebufferScaleY() {
        return framebufferScaleY;
    }

    public void setFramebufferScale(float framebufferScaleX, float framebufferScaleY) {
        this.framebufferScaleX = Math.max(0.1f, framebufferScaleX);
        this.framebufferScaleY = Math.max(0.1f, framebufferScaleY);
    }

    public boolean rightDown() {
        return rightDown;
    }

    public boolean rightPressed() {
        return rightPressed;
    }

    public boolean rightReleased() {
        return rightReleased;
    }

    public void setRightMouse(boolean down, boolean pressed, boolean released) {
        rightDown = down;
        rightPressed = pressed;
        rightReleased = released;
    }

    public CreateNodeDialog getCreateNodeDialog() {
        return createNodeDialog;
    }

    public void setCreateNodeDialog(CreateNodeDialog dialog) {
        this.createNodeDialog = dialog;
    }

    public AssetsClient assets() {
        return assets;
    }

    public void setAssets(AssetsClient assets) {
        this.assets = assets;
    }

    public boolean saveCurrentScene() {
        Session session = this.session;
        if (session == null || session.state() != SessionState.CONNECTED) {
            return false;
        }
        String sceneId = state.activeSceneId;
        if (sceneId == null || sceneId.isBlank()) {
            return false;
        }
        net.saveScene(session, sceneId);
        return true;
    }
}
