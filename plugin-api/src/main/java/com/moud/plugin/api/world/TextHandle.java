package com.moud.plugin.api.world;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.ui.TextAlign;

public interface TextHandle extends AutoCloseable {
    void setText(String newContent);
    void setPosition(Vector3 newPosition);
    void setColor(int r, int g, int b);
    void setShadow(boolean enabled);
    void setSeeThrough(boolean enabled);
    void setBackgroundColor(int argb);
    void setTextOpacity(int opacity);
    void setLineWidth(int width);
    void setAlignment(TextAlign alignment);
    void setBillboard(String billboard);
    void enableHitbox(double width, double height);
    void disableHitbox();
    void remove();
    Vector3 getPosition();
    String getInteractionUuid();

    @Override
    default void close() {
        remove();
    }
}
