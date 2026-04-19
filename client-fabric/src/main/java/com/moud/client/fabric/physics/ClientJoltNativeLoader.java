package com.moud.client.fabric.physics;

import com.moud.client.fabric.util.ClientDebugLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ClientJoltNativeLoader {
    private static final String LOG = "physics-native";
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final String VERSION = System.getProperty("moud.physics.nativeVersion", "3.5.0");
    private static final String DEFAULT_CLASSIFIER = System.getProperty("moud.physics.nativeClassifier", "ReleaseSp");
    private static final String PROP_NATIVE_PATH = "moud.physics.native";
    private static final String PROP_NATIVE_DIR = "moud.physics.nativeDir";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private ClientJoltNativeLoader() {}

    static boolean loadOnce() {
        if (LOADED.get()) return true;
        synchronized (ClientJoltNativeLoader.class) {
            if (LOADED.get()) return true;

            Optional<Path> explicit = explicitNativePath();
            if (explicit.isPresent()) {
                loadFromPath(explicit.get());
                return true;
            }

            try {
                System.loadLibrary("joltjni");
                ClientDebugLog.info(LOG, "Loaded joltjni from java.library.path");
                LOADED.set(true);
                return true;
            } catch (UnsatisfiedLinkError ignored) {
            }

            PlatformVariant variant = detectVariant().orElse(null);
            if (variant == null) {
                ClientDebugLog.warn(LOG, "Unsupported OS/arch for jolt-jni");
                return false;
            }

            Path localLibrary = resolveLocalPath(variant);
            if (!Files.exists(localLibrary)) {
                try {
                    downloadAndExtract(variant, localLibrary);
                } catch (Exception ex) {
                    ClientDebugLog.error(LOG, "Failed to auto-download jolt-jni native library. "
                            + "Set MOUD_JOLT_NATIVE to the extracted native path to bypass this.", ex);
                    return false;
                }
            }

            loadFromPath(localLibrary);
            return true;
        }
    }

    private static Optional<Path> explicitNativePath() {
        String env = System.getenv("MOUD_JOLT_NATIVE");
        if (env != null && !env.isBlank()) {
            Path path = Paths.get(env.trim());
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("MOUD_JOLT_NATIVE does not exist: " + path.toAbsolutePath());
            }
            return Optional.of(path);
        }
        String configured = System.getProperty(PROP_NATIVE_PATH);
        if (configured == null || configured.isBlank()) return Optional.empty();
        Path path = Paths.get(configured.trim());
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Configured -D" + PROP_NATIVE_PATH + " does not exist: " + path.toAbsolutePath());
        }
        return Optional.of(path);
    }

    private static Path resolveLocalPath(PlatformVariant variant) {
        String baseDir = System.getProperty(PROP_NATIVE_DIR, "libs/jolt");
        return Paths.get(baseDir, variant.directoryName + "-" + DEFAULT_CLASSIFIER, variant.libraryFileName);
    }

    private static void loadFromPath(Path path) {
        System.load(path.toAbsolutePath().toString());
        ClientDebugLog.info(LOG, "Loaded joltjni from " + path.toAbsolutePath());
        LOADED.set(true);
    }

    private static void downloadAndExtract(PlatformVariant variant, Path targetPath) throws Exception {
        Files.createDirectories(targetPath.getParent());
        String artifact = String.format("%s-%s-%s.jar", variant.artifactId, VERSION, DEFAULT_CLASSIFIER);
        String url = String.format("https://repo1.maven.org/maven2/com/github/stephengold/%s/%s/%s",
                variant.artifactId, VERSION, artifact);
        ClientDebugLog.info(LOG, "Downloading jolt-jni native: " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "MOUD/ClientPhysicsNativeLoader")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " while downloading " + url);
        }

        Path tempJar = Files.createTempFile("joltjni", ".jar");
        try {
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tempJar)) {
                in.transferTo(out);
            }
            boolean extracted = false;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempJar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.getName().endsWith(variant.librarySuffix())) continue;
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream out = Files.newOutputStream(targetPath)) { zis.transferTo(out); }
                    extracted = true;
                    break;
                }
            }
            if (!extracted) throw new IOException("Native library not found inside artifact: " + url);
        } finally {
            try { Files.deleteIfExists(tempJar); } catch (Exception ignored) {}
        }
        ClientDebugLog.info(LOG, "Installed jolt-jni native to " + targetPath.toAbsolutePath());
    }

    private static Optional<PlatformVariant> detectVariant() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return arch.contains("64") ? Optional.of(PlatformVariant.WINDOWS_X64) : Optional.empty();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Optional.of(arch.contains("aarch") || arch.contains("arm") ? PlatformVariant.MAC_ARM64 : PlatformVariant.MAC_X64);
        }
        if (osName.contains("linux")) {
            return Optional.of(arch.contains("aarch") || arch.contains("arm") ? PlatformVariant.LINUX_ARM64 : PlatformVariant.LINUX_X64);
        }
        return Optional.empty();
    }

    private enum PlatformVariant {
        LINUX_X64("linux-x86_64", "jolt-jni-Linux64", "libjoltjni.so"),
        LINUX_ARM64("linux-aarch64", "jolt-jni-Linux_ARM64", "libjoltjni.so"),
        WINDOWS_X64("windows-x86_64", "jolt-jni-Windows64", "joltjni.dll"),
        MAC_X64("macos-x86_64", "jolt-jni-MacOSX", "libjoltjni.dylib"),
        MAC_ARM64("macos-aarch64", "jolt-jni-MacOSX_ARM64", "libjoltjni.dylib");

        final String directoryName;
        final String artifactId;
        final String libraryFileName;

        PlatformVariant(String directoryName, String artifactId, String libraryFileName) {
            this.directoryName = directoryName;
            this.artifactId = artifactId;
            this.libraryFileName = libraryFileName;
        }

        String librarySuffix() {
            int dot = libraryFileName.lastIndexOf('.');
            return dot < 0 ? libraryFileName : libraryFileName.substring(dot);
        }
    }
}
