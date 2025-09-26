package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class TextProxy {
    private final Entity textEntity;
    private final TextDisplayMeta meta;

    public TextProxy(Vector3 position, String content, String billboard) {
        this.textEntity = new Entity(EntityType.TEXT_DISPLAY);
        this.meta = (TextDisplayMeta) this.textEntity.getEntityMeta();

        meta.setText(Component.text(content));
        meta.setBillboardRenderConstraints(parseBillboard(billboard));
        meta.setBackgroundColor(0xFF00FFFF);
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
        textEntity.teleport(new Pos(newPosition.x, newPosition.y, newPosition.z));
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
    public void remove() {
        textEntity.remove();
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        Pos pos = textEntity.getPosition();
        return new Vector3((float)pos.x(), (float)pos.y(), (float)pos.z());
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