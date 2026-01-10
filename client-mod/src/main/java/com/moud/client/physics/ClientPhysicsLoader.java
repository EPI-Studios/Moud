package com.moud.client.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public final class ClientPhysicsLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPhysicsLoader.class);

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final String VERSION = "3.5.0";
    private static final String CLASSIFIER = "DebugSp";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private ClientPhysicsLoader() {
    }

    public static void ensureLoaded() {
        if (INITIALIZED.get()) {
            return;
        }
        synchronized (ClientPhysicsLoader.class) {
            if (INITIALIZED.get()) {
                return;
            }

            loadNativeLibrary();
            initializeJolt();

            INITIALIZED.set(true);
        }
    }

    public static boolean isLoaded() {
        return INITIALIZED.get();
    }

    private static void loadNativeLibrary() {
        if (LOADED.get()) {
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
                new IllegalStateException("Unsupported OS/architecture for jolt-jni client physics"));

        Path localLibrary = resolveLocalPath(variant);

        if (!Files.exists(localLibrary)) {
            try {
                downloadAndExtract(variant, localLibrary);
            } catch (Exception ex) {
                String message = "Failed to download jolt-jni native library. " +
                        "Please download '" + variant.artifactId + "' (" + CLASSIFIER + ") from Maven Central " +
                        "and place " + variant.libraryFileName + " at " + localLibrary.toAbsolutePath();
                LOGGER.error(message, ex);
                throw new IllegalStateException(message, ex);
            }
        }

        System.load(localLibrary.toAbsolutePath().toString());
        LOGGER.info("Loaded joltjni from {}", localLibrary.toAbsolutePath());
        LOADED.set(true);
    }

    private static void initializeJolt() {
        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Failed to initialize Jolt factory");
        }
        Jolt.registerTypes();
        LOGGER.info("Jolt Physics initialized for client-side collision");
    }

    private static Path resolveLocalPath(PlatformVariant variant) {
        Path baseDir = Paths.get("moud-natives", "jolt", variant.directoryName);
        return baseDir.resolve(variant.libraryFileName);
    }

    private static void downloadAndExtract(PlatformVariant variant, Path targetPath) throws Exception {
        Files.createDirectories(targetPath.getParent());

        String artifact = String.format("%s-%s-%s.jar", variant.artifactId, VERSION, CLASSIFIER);
        String url = String.format(
                "https://repo1.maven.org/maven2/com/github/stephengold/%s/%s/%s",
                variant.artifactId, VERSION, artifact
        );

        LOGGER.info("Downloading jolt-jni native from {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "MoudClientPhysics/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }

        Path tempJar = Files.createTempFile("joltjni-client", ".jar");
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
            throw new IOException("Native library not found in downloaded artifact");
        }
        LOGGER.info("Installed jolt-jni native to {}", targetPath.toAbsolutePath());
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
        MAC_X64("macos-x86_64", "jolt-jni-MacOSX64", "libjoltjni.dylib"),
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
            return dot >= 0 ? libraryFileName.substring(dot) : "";
        }
    }
}
