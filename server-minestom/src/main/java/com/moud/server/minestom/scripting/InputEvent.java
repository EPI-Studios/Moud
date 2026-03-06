package com.moud.server.minestom.scripting;


final class InputEvent {
    private final PlayerInputState input;

    InputEvent(PlayerInputState input) {
        this.input = input;
    }

    @HostAccess.Export
    public String playerUuid() {
        return input == null ? "" : input.playerUuid();
    }

    @HostAccess.Export
    public long clientTick() {
        return input == null ? 0L : input.clientTick();
    }

    @HostAccess.Export
    public float moveX() {
        return input == null ? 0.0f : input.moveX();
    }

    @HostAccess.Export
    public float moveZ() {
        return input == null ? 0.0f : input.moveZ();
    }

    @HostAccess.Export
    public float yawDeg() {
        return input == null ? 0.0f : input.yawDeg();
    }

    @HostAccess.Export
    public float pitchDeg() {
        return input == null ? 0.0f : input.pitchDeg();
    }

    @HostAccess.Export
    public boolean jump() {
        return input != null && input.jump();
    }

    @HostAccess.Export
    public boolean sprint() {
        return input != null && input.sprint();
    }
}
