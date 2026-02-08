package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.ui.TextAlign;
import com.moud.plugin.api.world.TextHandle;
import com.moud.server.proxy.TextProxy;

public final class TextHandleAdapter implements TextHandle {
    private final TextProxy proxy;

    public TextHandleAdapter(TextProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public void setText(String newContent) {
        proxy.setText(newContent);
    }

    @Override
    public void setPosition(Vector3 newPosition) {
        proxy.setPosition(newPosition);
    }

    @Override
    public void setColor(int r, int g, int b) {
        proxy.setColor(r, g, b);
    }

    @Override
    public void setShadow(boolean enabled) {
        proxy.setShadow(enabled);
    }

    @Override
    public void setSeeThrough(boolean enabled) {
        proxy.setSeeThrough(enabled);
    }

    @Override
    public void setBackgroundColor(int argb) {
        proxy.setBackgroundColor(argb);
    }

    @Override
    public void setTextOpacity(int opacity) {
        proxy.setTextOpacity(opacity);
    }

    @Override
    public void setLineWidth(int width) {
        proxy.setLineWidth(width);
    }

    @Override
    public void setAlignment(TextAlign alignment) {
        if (alignment != null) {
            proxy.setAlignment(alignment.wireName());
        }
    }

    @Override
    public void setBillboard(String billboard) {
        proxy.setBillboard(billboard);
    }

    @Override
    public void enableHitbox(double width, double height) {
        proxy.enableHitbox(width, height);
    }

    @Override
    public void disableHitbox() {
        proxy.disableHitbox();
    }

    @Override
    public void remove() {
        proxy.remove();
    }

    @Override
    public Vector3 getPosition() {
        return proxy.getPosition();
    }

    @Override
    public String getInteractionUuid() {
        return proxy.getInteractionUuid();
    }
}
