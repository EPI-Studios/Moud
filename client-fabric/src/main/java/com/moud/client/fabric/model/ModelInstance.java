package com.moud.client.fabric.model;

public final class ModelInstance {
    private String currentAnim = "";
    private String loopOverride = "";
    private float speed = 1.0f;
    private long startMs = 0L;

    public void update(String anim, String loopOverride, float speed) {
        String next = anim == null ? "" : anim.trim();
        if (!next.equals(this.currentAnim)) {
            this.currentAnim = next;
            this.startMs = System.currentTimeMillis();
        }
        this.loopOverride = loopOverride == null ? "" : loopOverride.trim();
        this.speed = (Float.isFinite(speed) && speed > 0f) ? speed : 1.0f;
    }

    public float currentTime(AnimationClip clip) {
        if (currentAnim.isEmpty() || clip == null) return 0f;
        float elapsed = (System.currentTimeMillis() - startMs) * 0.001f * speed;
        float dur = clip.duration();
        if (dur <= 0f) return 0f;
        String loop = loopOverride.isEmpty() ? clip.loopMode() : loopOverride;
        return switch (loop) {
            case "loop" -> elapsed % dur;
            case "hold" -> Math.min(elapsed, dur);
            default     -> elapsed; //once
        };
    }

    public String currentAnim() { return currentAnim; }
}
