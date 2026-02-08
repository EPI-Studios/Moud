package com.moud.plugin.api.services.rendering;

/**
 * Renders the Minecraft level from a camera into a framebuffer.
 */
public record ClientWorldPassOptions(String type,
                                     String stage,
                                     Integer order,
                                     Boolean enabled,
                                     String out,
                                     ClientPerspectiveCameraOptions camera,
                                     Float fov,
                                     Float near,
                                     Float far,
                                     Float renderDistance,
                                     Boolean drawLights,
                                     Boolean clear) implements ClientRenderPassOptions {

    public ClientWorldPassOptions {
        type = type == null ? "world" : type;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stage = ClientRenderStage.AFTER_LEVEL.id();
        private Integer order = 0;
        private Boolean enabled = true;
        private String out;
        private ClientPerspectiveCameraOptions camera;
        private Float fov;
        private Float near;
        private Float far;
        private Float renderDistance = 64.0f;
        private Boolean drawLights = false;
        private Boolean clear = true;

        public Builder stage(ClientRenderStage stage) {
            this.stage = stage != null ? stage.id() : this.stage;
            return this;
        }

        public Builder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder out(String out) {
            this.out = out;
            return this;
        }

        public Builder camera(ClientPerspectiveCameraOptions camera) {
            this.camera = camera;
            return this;
        }

        /**
         * Sets the FOV. Values above {@code 2π} are treated as degrees by the client runtime.
         */
        public Builder fov(float fov) {
            this.fov = fov;
            return this;
        }

        public Builder near(float near) {
            this.near = near;
            return this;
        }

        public Builder far(float far) {
            this.far = far;
            return this;
        }

        /**
         * View distance in blocks used for chunk culling and the far plane heuristic.
         */
        public Builder renderDistance(float renderDistance) {
            this.renderDistance = renderDistance;
            return this;
        }

        public Builder drawLights(boolean drawLights) {
            this.drawLights = drawLights;
            return this;
        }

        public Builder clear(boolean clear) {
            this.clear = clear;
            return this;
        }

        public ClientWorldPassOptions build() {
            return new ClientWorldPassOptions("world", stage, order, enabled, out, camera, fov, near, far, renderDistance, drawLights, clear);
        }
    }
}
