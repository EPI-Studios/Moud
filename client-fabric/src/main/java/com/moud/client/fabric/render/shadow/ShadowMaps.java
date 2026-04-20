package com.moud.client.fabric.render.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.render.veil.GlUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public final class ShadowMaps {

    public static final int ATLAS_SIZE = 2048;

    static final int[] SLOT_X = { 0,    1024, 0,    512 };
    static final int[] SLOT_Y = { 0,    0,    1024, 1024 };
    static final int[] SLOT_SIZE = { 1024, 1024, 512,  512 };

    public static final int MAX_SPOT_CASTERS = SLOT_SIZE.length;
    public static final int SHADER_MAX_SLOTS = MAX_SPOT_CASTERS;

    public static final Identifier SPOT_SHADOW_ATLAS_ID = Identifier.of("moud", "dynamic/shadow_spot_atlas");

    private static int atlasFbo = 0;
    private static int atlasDepthTex = 0;
    private static int cacheFbo = 0;
    private static int cacheDepthTex = 0;
    private static boolean atlasTextureRegistered = false;

    private static final Matrix4f[] spotViewProj = new Matrix4f[MAX_SPOT_CASTERS];
    private static final int[] spotLightIndex = new int[MAX_SPOT_CASTERS];
    private static final float[] spotTileU0 = new float[MAX_SPOT_CASTERS];
    private static final float[] spotTileV0 = new float[MAX_SPOT_CASTERS];
    private static final float[] spotTileSize = new float[MAX_SPOT_CASTERS];

    private static final Matrix4f[] cacheViewProj = new Matrix4f[MAX_SPOT_CASTERS];
    private static final int[] cacheLightIndex = new int[MAX_SPOT_CASTERS];
    private static final boolean[] cacheValid = new boolean[MAX_SPOT_CASTERS];
    private static long cachedStaticRevision = Long.MIN_VALUE;

    private static int spotCasterCount = 0;

    private static final Matrix4f IDENTITY_MAT = new Matrix4f();

    static {
        for (int i = 0; i < MAX_SPOT_CASTERS; i++) {
            spotViewProj[i] = new Matrix4f();
            cacheViewProj[i] = new Matrix4f();
            spotLightIndex[i] = -1;
            cacheLightIndex[i] = -1;
        }
    }

    private ShadowMaps() { }

    public static void ensureAllocated() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (!atlasTextureRegistered) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().registerTexture(SPOT_SHADOW_ATLAS_ID, new ShadowDepthTexture(0));
                atlasTextureRegistered = true;
            }
        }
        if (atlasFbo == 0) {
            atlasDepthTex = allocDepth();
            atlasFbo = allocFbo(atlasDepthTex);
        }
        if (cacheFbo == 0) {
            cacheDepthTex = allocDepth();
            cacheFbo = allocFbo(cacheDepthTex);
        }
    }

    private static int allocDepth() {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                ATLAS_SIZE, ATLAS_SIZE, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private static int allocFbo(int depthTex) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthTex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    public static void dispose() {
        if (atlasFbo != 0) { GL30.glDeleteFramebuffers(atlasFbo); atlasFbo = 0; }
        if (atlasDepthTex != 0) { GL11.glDeleteTextures(atlasDepthTex); atlasDepthTex = 0; }
        if (cacheFbo != 0) { GL30.glDeleteFramebuffers(cacheFbo); cacheFbo = 0; }
        if (cacheDepthTex != 0) { GL11.glDeleteTextures(cacheDepthTex); cacheDepthTex = 0; }
        spotCasterCount = 0;
        for (int i = 0; i < MAX_SPOT_CASTERS; i++) cacheValid[i] = false;
    }

    public static int atlasFbo() { return atlasFbo; }
    public static int atlasDepthTex() { return atlasDepthTex; }
    public static int cacheFbo() { return cacheFbo; }
    public static int cacheDepthTex() { return cacheDepthTex; }
    public static int atlasSize() { return ATLAS_SIZE; }

    public static int spotCasterCount() { return spotCasterCount; }
    public static int spotTileX(int slot) { return inRange(slot) ? SLOT_X[slot] : 0; }
    public static int spotTileY(int slot) { return inRange(slot) ? SLOT_Y[slot] : 0; }
    public static int spotTileSize(int slot) { return inRange(slot) ? SLOT_SIZE[slot] : 0; }

    public static Matrix4f spotViewProj(int slot) {
        return inRange(slot) ? spotViewProj[slot] : null;
    }
    public static int spotLightIndex(int slot) {
        return inRange(slot) ? spotLightIndex[slot] : -1;
    }

    public static boolean cacheValid(int slot) {
        return inRange(slot) && cacheValid[slot];
    }
    public static void markCacheValid(int slot) {
        if (inRange(slot)) cacheValid[slot] = true;
    }
    public static Matrix4f cachedViewProj(int slot) {
        return inRange(slot) ? cacheViewProj[slot] : null;
    }
    public static int cachedLightIndex(int slot) {
        return inRange(slot) ? cacheLightIndex[slot] : -1;
    }
    public static void storeCacheMeta(int slot, int lightIdx, Matrix4f viewProj) {
        if (!inRange(slot)) return;
        cacheLightIndex[slot] = lightIdx;
        cacheViewProj[slot].set(viewProj);
    }

    public static long cachedStaticRevision() { return cachedStaticRevision; }
    public static void updateCachedStaticRevision(long rev) { cachedStaticRevision = rev; }

    public static void invalidateAllCaches() {
        for (int i = 0; i < MAX_SPOT_CASTERS; i++) cacheValid[i] = false;
    }

    public static void resetFrame() {
        spotCasterCount = 0;
        for (int i = 0; i < MAX_SPOT_CASTERS; i++) spotLightIndex[i] = -1;
    }

    public static int addSpotCaster(int lightIndex, Matrix4f viewProj) {
        if (spotCasterCount >= MAX_SPOT_CASTERS) return -1;
        int slot = spotCasterCount++;
        spotLightIndex[slot] = lightIndex;
        spotViewProj[slot].set(viewProj);
        float tileFrac = (float) SLOT_SIZE[slot] / (float) ATLAS_SIZE;
        spotTileU0[slot] = SLOT_X[slot] / (float) ATLAS_SIZE;
        spotTileV0[slot] = SLOT_Y[slot] / (float) ATLAS_SIZE;
        spotTileSize[slot] = tileFrac;
        return slot;
    }

    public static void uploadSpotShadowScalarUniforms(int pid) {
        if (pid <= 0) return;
        int exposed = Math.min(spotCasterCount, SHADER_MAX_SLOTS);
        GlUtil.uniform1i(pid, "NumSpotShadows", exposed);
        for (int i = 0; i < SHADER_MAX_SLOTS; i++) {
            String idxName = "SpotShadowLightIdx[" + i + "]";
            String tileName = "SpotShadowTile[" + i + "]";
            String matName = "SpotShadowMatrix[" + i + "]";
            GlUtil.uniform1i(pid, idxName, i < exposed ? spotLightIndex[i] : -1);
            if (i < exposed) {
                GlUtil.uniform4f(pid, tileName,
                        spotTileU0[i], spotTileV0[i],
                        spotTileU0[i] + spotTileSize[i],
                        spotTileV0[i] + spotTileSize[i]);
            } else {
                GlUtil.uniform4f(pid, tileName, 0f, 0f, 0f, 0f);
            }
            GlUtil.uniformMat4(pid, matName, i < exposed ? spotViewProj[i] : IDENTITY_MAT);
        }
    }

    public static Identifier spotShadowTextureId() {
        return SPOT_SHADOW_ATLAS_ID;
    }

    public static boolean hasActiveSpotShadow() {
        return spotCasterCount > 0;
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < MAX_SPOT_CASTERS;
    }
}
