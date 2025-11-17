package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@TsExpose
public class TextProxy {
    private final Entity textEntity;
    private final TextDisplayMeta meta;
    private Entity interactionEntity;

    public TextProxy(Vector3 position, String content, String billboard) {
        this.textEntity = new Entity(EntityType.TEXT_DISPLAY);
        this.meta = (TextDisplayMeta) this.textEntity.getEntityMeta();

        meta.setText(Component.text(content));
        meta.setBillboardRenderConstraints(parseBillboard(billboard));
        meta.setShadow(true);
        meta.setSeeThrough(false);
        meta.setLineWidth(200);
        meta.setPosRotInterpolationDuration(1);
        meta.setHasNoGravity(true);
        meta.setTransformationInterpolationDuration(1);
    }

    @HostAccess.Export
    public void setText(String newContent) {
        meta.setText(Component.text(newContent));
    }

    @HostAccess.Export
    public void setPosition(Vector3 newPosition) {
        Pos currentPos = textEntity.getPosition();
        Pos newPos = new Pos(newPosition.x, newPosition.y, newPosition.z, currentPos.yaw(), currentPos.pitch());
        textEntity.teleport(newPos);

        if (interactionEntity != null && interactionEntity.getInstance() != null) {
            interactionEntity.teleport(newPos);
        }
    }

    @HostAccess.Export
    public void setColor(Value colorValue) {
        if (colorValue != null && colorValue.hasMembers()) {
            int r = colorValue.hasMember("r") ? colorValue.getMember("r").asInt() : 255;
            int g = colorValue.hasMember("g") ? colorValue.getMember("g").asInt() : 255;
            int b = colorValue.hasMember("b") ? colorValue.getMember("b").asInt() : 255;
            meta.setText(meta.getText().color(TextColor.color(r, g, b)));
        }
    }

    @HostAccess.Export
    public void setColor(int r, int g, int b) {
        meta.setText(meta.getText().color(TextColor.color(r, g, b)));
    }

    @HostAccess.Export
    public void enableHitbox() {
        String content = meta.getText().toString();
        int lineCount = Math.max(1, content.split("\n").length);
        int lineWidth = meta.getLineWidth();


        final float PIXELS_PER_BLOCK = 64f;
        final float WIDTH_CORRECTION = 0.9f;
        final float HEIGHT_CORRECTION = 1.6f;
        final float LINE_HEIGHT_BLOCKS = 0.05f;

        float scaleX = 1.0f;
        float scaleY = 1.0f;

        float width  = ((lineWidth / PIXELS_PER_BLOCK) * scaleX) * WIDTH_CORRECTION;
        float height = ((LINE_HEIGHT_BLOCKS * lineCount) * scaleY) * HEIGHT_CORRECTION;

        enableHitbox(width, height);
    }


    @HostAccess.Export
    public void enableHitbox(double width, double height) {
        if (interactionEntity != null) {
            interactionEntity.remove();
        }

        interactionEntity = new Entity(EntityType.INTERACTION);
        var interactionMeta = (net.minestom.server.entity.metadata.other.InteractionMeta) interactionEntity.getEntityMeta();
        interactionMeta.setWidth((float) width);
        interactionMeta.setHeight((float) height);
        interactionMeta.setHasNoGravity(true);
        interactionMeta.setResponse(true);

        Pos currentPos = textEntity.getPosition();
        if (textEntity.getInstance() != null) {
            interactionEntity.setInstance(textEntity.getInstance(), currentPos);
        }
    }

    @HostAccess.Export
    public void disableHitbox() {
        if (interactionEntity != null) {
            interactionEntity.remove();
            interactionEntity = null;
        }
    }

    @HostAccess.Export
    public void remove() {
        if (interactionEntity != null) {
            interactionEntity.remove();
        }
        textEntity.remove();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        Pos pos = textEntity.getPosition();
        return new Vector3((float)pos.x(), (float)pos.y(), (float)pos.z());
    }

    @HostAccess.Export
    public String getInteractionUuid() {
        return interactionEntity != null ? interactionEntity.getUuid().toString() : null;
    }

    public Entity getEntity() {
        return textEntity;
    }

    private TextDisplayMeta.BillboardConstraints parseBillboard(String billboard) {
        if (billboard == null) return TextDisplayMeta.BillboardConstraints.FIXED;
        return switch (billboard.toLowerCase()) {
            case "vertical" -> TextDisplayMeta.BillboardConstraints.VERTICAL;
            case "horizontal" -> TextDisplayMeta.BillboardConstraints.HORIZONTAL;
            case "center" -> TextDisplayMeta.BillboardConstraints.CENTER;
            default -> TextDisplayMeta.BillboardConstraints.FIXED;
        };
    }
}