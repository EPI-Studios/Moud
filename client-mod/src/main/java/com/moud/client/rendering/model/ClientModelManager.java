package com.moud.client.rendering.model;

import com.moud.api.rendering.mesh.Mesh;
import com.moud.api.rendering.model.ModelLoader;
import com.moud.api.rendering.model.ModelLoaderRegistry;
import com.moud.api.rendering.model.ModelLoaders;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-side helper for loading meshes using the shared model loader registry.
 */
public final class ClientModelManager {
    private final ModelLoaderRegistry registry;

    public ClientModelManager() {
        this(ModelLoaders.registry());
    }

    public ClientModelManager(ModelLoaderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Mesh load(String identifier, Supplier<InputStream> streamSupplier) throws IOException {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(streamSupplier, "streamSupplier");
        String extension = extractExtension(identifier)
                .orElseThrow(() -> new IllegalArgumentException("Identifier has no extension: " + identifier));
        ModelLoader loader = registry.require(extension);
        try (InputStream stream = streamSupplier.get()) {
            if (stream == null) {
                throw new IOException("Resource not found: " + identifier);
            }
            return loader.load(stream);
        }
    }

    public void registerLoader(String extension, ModelLoader loader) {
        registry.register(extension, loader);
    }

    public void unregisterLoader(String extension) {
        registry.unregister(extension);
    }

    private static Optional<String> extractExtension(String identifier) {
        int lastDot = identifier.lastIndexOf('.');
        if (lastDot == -1 || lastDot == identifier.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(identifier.substring(lastDot + 1).toLowerCase(Locale.ROOT));
    }
}
