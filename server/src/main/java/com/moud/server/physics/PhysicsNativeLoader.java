package com.moud.server.physics;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;

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

final class PhysicsNativeLoader {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PhysicsNativeLoader.class,
            LogContext.builder().put("subsystem", "physics").put("component", "native-loader").build()
    );

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final String VERSION = System.getProperty("moud.physics.nativeVersion", "3.5.0");
    private static final String DEFAULT_CLASSIFIER = System.getProperty("moud.physics.nativeClassifier", "DebugSp");
    private static final String PROP_NATIVE_PATH = "moud.physics.native";
    private static final String PROP_NATIVE_DIR = "moud.physics.nativeDir";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private PhysicsNativeLoader() {
    }

    static void loadLibrary() {
        if (LOADED.get()) {
            return;
        }
        synchronized (PhysicsNativeLoader.class) {
            if (LOADED.get()) {
                return;
            }

            Optional<Path> explicit = explicitNativePath();
            if (explicit.isPresent()) {
                loadFromPath(explicit.get());
                return;
            }

            try {
                System.loadLibrary("joltjni");
                LOGGER.info("Loaded joltjni from system library path");
                LOADED.set(true);
                return;
            } catch (UnsatisfiedLinkError ignored) {
                LOGGER.debug("joltjni not on java.library.path, attempting managed download");
            }

            PlatformVariant variant = detectVariant().orElseThrow(() ->
                    new IllegalStateException("Unsupported operating system/architecture for jolt-jni"));
            Path localLibrary = resolveLocalPath(variant);

            if (!Files.exists(localLibrary)) {
                try {
                    downloadAndExtract(variant, localLibrary);
                } catch (Exception ex) {
                    String message = "Failed to download jolt-jni native library automatically. " +
                            "Please download the '" + variant.artifactId + "' artifact (" + DEFAULT_CLASSIFIER + " classifier) from " +
                            "https://repo1.maven.org/maven2/com/github/stephengold/" + variant.artifactId + "/" + VERSION + "/ " +
                            "and place the extracted " + variant.libraryFileName + " at " + localLibrary.toAbsolutePath();
                    LOGGER.error(message, ex);
                    throw new IllegalStateException(message, ex);
                }
            }

            loadFromPath(localLibrary);
        }
    }

    private static Optional<Path> explicitNativePath() {
        String configured = System.getProperty(PROP_NATIVE_PATH);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Configured moud.physics.native does not exist: " + path.toAbsolutePath());
        }
        return Optional.of(path);
    }

    private static void loadFromPath(Path path) {
        System.load(path.toAbsolutePath().toString());
        LOGGER.info(LogContext.builder().put("path", path.toAbsolutePath()).build(),
                "Loaded joltjni from {}", path.toAbsolutePath());
        LOADED.set(true);
    }

    private static Path resolveLocalPath(PlatformVariant variant) {
        String baseDir = System.getProperty(PROP_NATIVE_DIR, "libs/jolt");
        Path targetDir = Paths.get(baseDir, variant.directoryName);
        return targetDir.resolve(variant.libraryFileName);
    }

    private static void downloadAndExtract(PlatformVariant variant, Path targetPath) throws Exception {
        Files.createDirectories(targetPath.getParent());
        String classifier = DEFAULT_CLASSIFIER;
        String artifact = String.format("%s-%s-%s.jar", variant.artifactId, VERSION, classifier);
        String url = String.format(
                "https://repo1.maven.org/maven2/com/github/stephengold/%s/%s/%s",
                variant.artifactId,
                VERSION,
                artifact
        );
        LOGGER.info(LogContext.builder().put("url", url).build(),
                "Downloading jolt-jni native ({}/{})", variant.artifactId, classifier);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "MoudPhysicsDownloader/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " while downloading " + url);
        }

        Path tempJar = Files.createTempFile("joltjni", ".jar");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tempJar)) {
            in.transferTo(out);
        }

        boolean extracted = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempJar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().endsWith(variant.librarySuffix())) {
                    continue;
                }
                Files.createDirectories(targetPath.getParent());
                try (OutputStream out = Files.newOutputStream(targetPath)) {
                    zis.transferTo(out);
                }
                extracted = true;
                break;
            }
        } finally {
            Files.deleteIfExists(tempJar);
        }

        if (!extracted) {
            throw new IOException("Native library not found inside downloaded artifact");
        }
        LOGGER.info(LogContext.builder().put("path", targetPath.toAbsolutePath()).build(),
                "Installed jolt-jni native to {}", targetPath.toAbsolutePath());
    }

    private static Optional<PlatformVariant> detectVariant() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            if (arch.contains("64")) {
                return Optional.of(PlatformVariant.WINDOWS_X64);
            }
            return Optional.empty();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            if (arch.contains("aarch") || arch.contains("arm")) {
                return Optional.of(PlatformVariant.MAC_ARM64);
            }
            return Optional.of(PlatformVariant.MAC_X64);
        }
        if (osName.contains("linux")) {
            if (arch.contains("aarch") || arch.contains("arm")) {
                return Optional.of(PlatformVariant.LINUX_ARM64);
            }
            return Optional.of(PlatformVariant.LINUX_X64);
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
            if (libraryFileName.endsWith(".so")) return ".so";
            if (libraryFileName.endsWith(".dylib")) return ".dylib";
            if (libraryFileName.endsWith(".dll")) return ".dll";
            return libraryFileName.substring(libraryFileName.lastIndexOf('.'));
        }
    }
}