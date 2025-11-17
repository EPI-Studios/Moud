package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.server.assets.AssetManager;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;

@TsExpose
public class AssetProxy {
    private final AssetManager assetManager;

    public AssetProxy(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    @HostAccess.Export
    public ShaderAssetProxy loadShader(String path) {
        try {
            AssetManager.ShaderAsset asset = (AssetManager.ShaderAsset) assetManager.loadAsset(path);
            return new ShaderAssetProxy(asset);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }

    @HostAccess.Export
    public TextureAssetProxy loadTexture(String path) {
        try {
            AssetManager.TextureAsset asset = (AssetManager.TextureAsset) assetManager.loadAsset(path);
            return new TextureAssetProxy(asset);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + path, e);
        }
    }

    @HostAccess.Export
    public DataAssetProxy loadData(String path) {
        try {
            AssetManager.DataAsset asset = (AssetManager.DataAsset) assetManager.loadAsset(path);
            return new DataAssetProxy(asset);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load data: " + path, e);
        }
    }

    public static class ShaderAssetProxy {
        private final AssetManager.ShaderAsset asset;
        public ShaderAssetProxy(AssetManager.ShaderAsset asset) { this.asset = asset; }

        @HostAccess.Export
        public String getId() { return asset.getId(); }

        @HostAccess.Export
        public String getCode() { return asset.getShaderCode(); }
    }

    public static class TextureAssetProxy {
        private final AssetManager.TextureAsset asset;
        public TextureAssetProxy(AssetManager.TextureAsset asset) { this.asset = asset; }

        @HostAccess.Export
        public String getId() { return asset.getId(); }

        @HostAccess.Export
        public byte[] getData() { return asset.getData(); }
    }

    public static class DataAssetProxy {
        private final AssetManager.DataAsset asset;
        public DataAssetProxy(AssetManager.DataAsset asset) { this.asset = asset; }

        @HostAccess.Export
        public String getId() { return asset.getId(); }

        @HostAccess.Export
        public String getContent() { return asset.getJsonContent(); }
    }
}