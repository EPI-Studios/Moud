import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LoadNatives {
    static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    static final String SYSTEM = OS.contains("win") ? "windows" : OS.contains("mac") ? "macos" : "linux";
    static final String ARCH = System.getProperty("os.arch").contains("aarch64") ? "arm64" : "x64";
    static final List<String> WANTED = List.of("natives/" + SYSTEM + "-" + ARCH + "/", "net/hollowcube/luau/" + SYSTEM + "/" + ARCH + "/");
    static final List<Path> FOUND = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("natives");
        try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
            walk(in, dir);
        }
        int failed = 0;
        for (Path lib : FOUND) {
            try {
                System.load(lib.toString());
                System.out.println("loaded " + lib.getFileName());
            } catch (UnsatisfiedLinkError e) {
                failed++;
                System.out.println("failed " + lib.getFileName() + ": " + e.getMessage());
            }
        }
        if (FOUND.size() < 4) throw new IllegalStateException("only " + FOUND.size() + " natives for " + SYSTEM + "-" + ARCH);
        if (failed > 0) System.exit(1);
    }

    static void walk(InputStream in, Path dir) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String name = entry.getName();
            if (name.startsWith("META-INF/jars/") && name.endsWith(".jar")) {
                walk(new ByteArrayInputStream(zip.readAllBytes()), dir);
            } else if (!entry.isDirectory() && WANTED.stream().anyMatch(name::startsWith)) {
                Path file = Files.createDirectories(dir.resolve(String.valueOf(FOUND.size()))).resolve(Path.of(name).getFileName().toString());
                Files.write(file, zip.readAllBytes());
                FOUND.add(file);
            }
        }
    }
}
