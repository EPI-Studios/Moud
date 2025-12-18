package com.moud.server.plugin.impl;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.WorldService;
import com.moud.plugin.api.ui.TextAlign;
import com.moud.plugin.api.world.DisplayAnchor;
import com.moud.plugin.api.world.DisplayBillboardMode;
import com.moud.plugin.api.world.DisplayContent;
import com.moud.plugin.api.world.DisplayHandle;
import com.moud.plugin.api.world.DisplayOptions;
import com.moud.plugin.api.world.DisplayPlayback;
import com.moud.plugin.api.world.TextHandle;
import com.moud.plugin.api.world.TextOptions;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.proxy.MediaDisplayProxy;
import com.moud.server.proxy.ModelProxy;
import com.moud.server.proxy.TextProxy;
import net.minestom.server.instance.Instance;

public final class WorldServiceImpl implements WorldService {
    private Instance target() {
        return InstanceManager.getInstance().getDefaultInstance();
    }

    @Override
    public long getTime() {
        return target().getTime();
    }

    @Override
    public void setTime(long time) {
        target().setTime(time);
    }

    @Override
    public int getTimeRate() {
        return target().getTimeRate();
    }

    @Override
    public void setTimeRate(int timeRate) {
        if (timeRate < 0) {
            throw new IllegalArgumentException("timeRate must be non-negative");
        }
        target().setTimeRate(timeRate);
    }

    @Override
    public int getTimeSynchronizationTicks() {
        return target().getTimeSynchronizationTicks();
    }

    @Override
    public void setTimeSynchronizationTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        target().setTimeSynchronizationTicks(ticks);
    }

    @Override
    public DisplayHandle createDisplay(DisplayOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }

        Vector3 position = options.position() != null ? options.position() : Vector3.zero();
        Quaternion rotation = options.rotation() != null ? options.rotation() : Quaternion.identity();
        Vector3 scale = options.scale() != null ? options.scale() : Vector3.one();

        MediaDisplayProxy proxy = new MediaDisplayProxy(target(), position, rotation, scale);

        applyBillboard(proxy, options.billboard());
        proxy.setRenderThroughBlocks(options.renderThroughBlocks());
        applyAnchor(proxy, options.anchor());
        applyContent(proxy, options.content());
        applyPlayback(proxy, options.playback());

        return new DisplayHandleAdapter(proxy);
    }

    @Override
    public TextHandle createText(TextOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
        Vector3 position = options.position() != null ? options.position() : Vector3.zero();
        String content = options.content() != null ? options.content() : "";
        String billboard = options.billboard() != null ? options.billboard() : "fixed";

        TextProxy proxy = new TextProxy(position, content, billboard);
        proxy.getEntity().setInstance(target(), new net.minestom.server.coordinate.Pos(position.x, position.y, position.z, 0, 0));

        if (options.shadow() != null) proxy.setShadow(options.shadow());
        if (options.seeThrough() != null) proxy.setSeeThrough(options.seeThrough());
        if (options.backgroundColor() != null) proxy.setBackgroundColor(options.backgroundColor());
        if (options.textOpacity() != null) proxy.setTextOpacity(options.textOpacity());
        if (options.lineWidth() != null) proxy.setLineWidth(options.lineWidth());
        if (options.alignment() != null) proxy.setAlignment(mapAlignment(options.alignment()));

        Double hitboxW = options.hitboxWidth();
        Double hitboxH = options.hitboxHeight();
        if (hitboxW != null || hitboxH != null) {
            proxy.enableHitbox(hitboxW != null ? hitboxW : 1.0, hitboxH != null ? hitboxH : 1.0);
        }

        return new TextHandleAdapter(proxy);
    }

    private void applyBillboard(MediaDisplayProxy proxy, DisplayBillboardMode mode) {
        if (mode == null) {
            return;
        }
        switch (mode) {
            case CAMERA -> proxy.setBillboard("camera");
            case VERTICAL -> proxy.setBillboard("vertical");
            default -> proxy.setBillboard("none");
        }
    }

    private void applyAnchor(MediaDisplayProxy proxy, DisplayAnchor anchor) {
        if (anchor == null || anchor.type() == null) {
            return;
        }
        Vector3 offset = anchor.offset() != null ? anchor.offset() : Vector3.zero();
        boolean local = anchor.local();
        boolean inheritRotation = anchor.inheritRotation();
        boolean inheritScale = anchor.inheritScale();
        boolean includePitch = anchor.includePitch();
        switch (anchor.type()) {
            case BLOCK -> proxy.setAnchorToBlock(anchor.x(), anchor.y(), anchor.z(), offset);
            case ENTITY, PLAYER -> {
                if (anchor.uuid() != null && !anchor.uuid().isBlank()) {
                    proxy.setAnchorToEntity(java.util.UUID.fromString(anchor.uuid()), offset, local, inheritRotation, inheritScale, includePitch);
                }
            }
            case MODEL -> {
                Long modelId = anchor.modelId();
                if (modelId == null) {
                    proxy.clearAnchor();
                    return;
                }
                ModelProxy model = ModelManager.getInstance().getById(modelId);
                if (model != null) {
                    proxy.setAnchorToModel(model, offset, local, inheritRotation, inheritScale, includePitch);
                } else {
                    proxy.clearAnchor();
                }
            }
            default -> proxy.clearAnchor();
        }
    }

    private void applyContent(MediaDisplayProxy proxy, DisplayContent content) {
        if (content == null || content.type() == null) {
            return;
        }
        double fps = content.fps();
        boolean loop = content.loop();
        switch (content.type()) {
            case URL -> {
                proxy.setVideo(content.source(), fps, loop);
            }
            case FRAME_SEQUENCE -> {
                if (content.frames() != null && !content.frames().isEmpty()) {
                    proxy.setFrameSequence(content.frames().toArray(String[]::new), fps, loop);
                }
            }
            case IMAGE -> proxy.setImage(content.source());
            default -> proxy.setImage(content.source());
        }
    }

    private void applyPlayback(MediaDisplayProxy proxy, DisplayPlayback playback) {
        if (playback == null) {
            return;
        }
        proxy.setPlaybackSpeed(playback.speed());
        proxy.seek(playback.offsetSeconds());
        if (playback.loop() != null) {
            proxy.setLoop(playback.loop());
        }
        if (playback.fps() != null) {
            proxy.setFrameRate(playback.fps());
        }
        if (playback.playing()) {
            proxy.play();
        }
    }

    private String mapAlignment(TextAlign align) {
        return align != null ? align.wireName() : null;
    }
}
