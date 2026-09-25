package com.meekdev.moud.mod.client.editor.files;

import com.meekdev.moud.core.scene.Json;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceToml;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CodeEditor {

    public static final Path PREFERENCES = Path.of(System.getProperty("user.home"), ".moud", "preferences.json");
    public static final String WHERE = "File > Code Editor";

    private static final String CHOICE_KEY = "codeEditor";
    private static final String COMMAND_KEY = "codeEditorCommand";
    private static final String SYSTEM = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean MAC = SYSTEM.contains("mac");
    private static final boolean WINDOWS = SYSTEM.contains("win");
    private static final String HOME = System.getProperty("user.home");
    private static final String PROGRAMS = Path.of(HOME, "AppData", "Local", "Programs").toString();
    private static final String PROGRAM_FILES = System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files");
    private static final String TOOLBOX = MAC
            ? HOME + "/Library/Application Support/JetBrains/Toolbox/scripts/"
            : WINDOWS ? Path.of(HOME, "AppData", "Local", "JetBrains", "Toolbox", "scripts").toString() + File.separator
            : HOME + "/.local/share/JetBrains/Toolbox/scripts/";
    private static final List<String> WINDOWS_EXTENSIONS = List.of("", ".exe", ".cmd", ".bat");
    private static final List<Choice> AUTO_ORDER = List.of(Choice.ZED, Choice.VS_CODE, Choice.VSCODIUM, Choice.CURSOR, Choice.SUBLIME);

    private enum Syntax { FOLDER_FILE_LINE, GOTO, JETBRAINS, FILE }

    public enum Choice {
        AUTO("auto", "Auto", "", Syntax.FILE),
        ZED("zed", "Zed", "zed", Syntax.FOLDER_FILE_LINE),
        VS_CODE("vscode", "VS Code", "code", Syntax.GOTO),
        VSCODIUM("vscodium", "VSCodium", "codium", Syntax.GOTO),
        CURSOR("cursor", "Cursor", "cursor", Syntax.GOTO),
        SUBLIME("sublime", "Sublime Text", "subl", Syntax.FOLDER_FILE_LINE),
        INTELLIJ("intellij", "IntelliJ IDEA", "idea", Syntax.JETBRAINS),
        WEBSTORM("webstorm", "WebStorm", "webstorm", Syntax.JETBRAINS),
        SYSTEM("system", "System default", "", Syntax.FILE),
        CUSTOM("custom", "Custom command", "", Syntax.FILE);

        private final String id;
        private final String label;
        private final String program;
        private final Syntax syntax;

        Choice(String id, String label, String program, Syntax syntax) {
            this.id = id;
            this.label = label;
            this.program = program;
            this.syntax = syntax;
        }

        public String label() {
            return label;
        }

        public String program() {
            return program;
        }
    }

    public record Launch(List<String> command, @Nullable String problem) {

        static Launch of(List<String> command) {
            return new Launch(command, null);
        }

        static Launch missing(String problem) {
            return new Launch(List.of(), problem);
        }
    }

    private static Choice choice = Choice.AUTO;
    private static String custom = "";
    private static boolean loaded;

    private CodeEditor() {}

    public static Choice choice() {
        load();
        return choice;
    }

    public static String custom() {
        load();
        return custom;
    }

    public static void set(Choice chosen, String command) throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        if (Files.isRegularFile(PREFERENCES)) {
            try {
                if (Json.parse(Files.readString(PREFERENCES)) instanceof Map<?, ?> read) {
                    for (Map.Entry<?, ?> entry : read.entrySet()) document.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } catch (RuntimeException e) {
                MoudMod.LOG.warn("{} could not be read, it is rewritten", PREFERENCES, e);
            }
        }
        document.put(CHOICE_KEY, chosen.id);
        document.put(COMMAND_KEY, command.strip());
        Files.createDirectories(PREFERENCES.getParent());
        Files.writeString(PREFERENCES, Json.write(document));
        choice = chosen;
        custom = command.strip();
        loaded = true;
    }

    public static void open(Path file, int line) {
        open(choice(), custom(), file, line);
    }

    public static void open(Choice chosen, String command, Path file, int line) {
        Launch launch = launch(chosen, command, file, line);
        if (launch.problem() != null) {
            warn(launch.problem());
            return;
        }
        try {
            new ProcessBuilder(launch.command()).start();
        } catch (IOException e) {
            warn(chosen.label() + " could not start: " + e.getMessage());
        }
    }

    public static Launch launch(Choice chosen, String command, Path file, int line) {
        String folder = PlaceToml.root().toAbsolutePath().toString();
        String path = file.toAbsolutePath().toString();
        int at = Math.max(1, line);
        switch (chosen) {
            case AUTO -> {
                for (Choice app : AUTO_ORDER) {
                    List<String> program = find(app);
                    if (program != null) return Launch.of(arguments(app.syntax, program, folder, path, at));
                }
                String notepad = WINDOWS ? onPath("notepad") : null;
                if (notepad != null) return Launch.of(List.of(notepad, path));
                List<String> fallback = systemDefault(path);
                if (fallback != null) return Launch.of(fallback);
                return Launch.missing("No code editor found to open " + path + ". Pick one in " + WHERE);
            }
            case SYSTEM -> {
                List<String> opener = systemDefault(path);
                return opener == null ? Launch.missing("xdg-open was not found to open " + path) : Launch.of(opener);
            }
            case CUSTOM -> {
                List<String> words = split(command);
                if (words.isEmpty()) return Launch.missing("The custom code editor command is empty. Set it in " + WHERE);
                boolean named = false;
                List<String> filled = new ArrayList<>();
                for (String word : words) {
                    named |= word.contains("{file}");
                    filled.add(word.replace("{file}", path).replace("{line}", Integer.toString(at)).replace("{folder}", folder));
                }
                if (!named) filled.add(path);
                String program = filled.getFirst();
                String found = program.contains("/") || program.contains("\\") ? null : onPath(program);
                if (found != null) filled.set(0, found);
                return Launch.of(filled);
            }
            default -> {
                List<String> program = find(chosen);
                if (program == null) {
                    return Launch.missing(chosen.label() + " was not found. Put " + chosen.program + " on your PATH or pick another editor in " + WHERE);
                }
                return Launch.of(arguments(chosen.syntax, program, folder, path, at));
            }
        }
    }

    public static List<String> split(String command) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean started = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else word.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                started = true;
            } else if (Character.isWhitespace(c)) {
                if (started) words.add(word.toString());
                word.setLength(0);
                started = false;
            } else {
                word.append(c);
                started = true;
            }
        }
        if (started) words.add(word.toString());
        return words;
    }

    public static String join(List<String> command) {
        List<String> shown = new ArrayList<>();
        for (String word : command) shown.add(word.isEmpty() || word.chars().anyMatch(Character::isWhitespace) ? "\"" + word + "\"" : word);
        return String.join(" ", shown);
    }

    private static List<String> arguments(Syntax syntax, List<String> program, String folder, String path, int line) {
        List<String> command = new ArrayList<>(program);
        switch (syntax) {
            case FOLDER_FILE_LINE -> command.addAll(List.of(folder, path + ":" + line));
            case GOTO -> command.addAll(List.of(folder, "--goto", path + ":" + line));
            case JETBRAINS -> command.addAll(List.of(folder, "--line", Integer.toString(line), path));
            case FILE -> command.add(path);
        }
        return command;
    }

    private static @Nullable List<String> systemDefault(String path) {
        if (MAC) return List.of("/usr/bin/open", "-t", path);
        if (WINDOWS) return List.of("cmd", "/c", "start", "\"\"", path);
        String opener = onPath("xdg-open");
        return opener == null ? null : List.of(opener, path);
    }

    private static @Nullable List<String> find(Choice app) {
        for (String candidate : candidates(app)) {
            if (candidate.startsWith("flatpak:")) {
                String id = candidate.substring("flatpak:".length());
                String flatpak = onPath("flatpak");
                boolean installed = Files.isDirectory(Path.of("/var/lib/flatpak/app", id))
                        || Files.isDirectory(Path.of(HOME, ".local/share/flatpak/app", id));
                if (flatpak != null && installed) return List.of(flatpak, "run", id);
                continue;
            }
            String found = candidate.contains("/") || candidate.contains("\\") ? executable(candidate) : onPath(candidate);
            if (found != null) return List.of(found);
        }
        return null;
    }

    private static List<String> candidates(Choice app) {
        if (MAC) return switch (app) {
            case ZED -> List.of("zed", "/usr/local/bin/zed", "/opt/homebrew/bin/zed", "/Applications/Zed.app/Contents/MacOS/cli",
                    HOME + "/Applications/Zed.app/Contents/MacOS/cli");
            case VS_CODE -> List.of("code", "/usr/local/bin/code", "/opt/homebrew/bin/code",
                    "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code",
                    HOME + "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code");
            case VSCODIUM -> List.of("codium", "/usr/local/bin/codium", "/opt/homebrew/bin/codium",
                    "/Applications/VSCodium.app/Contents/Resources/app/bin/codium");
            case CURSOR -> List.of("cursor", "/usr/local/bin/cursor", "/opt/homebrew/bin/cursor",
                    "/Applications/Cursor.app/Contents/Resources/app/bin/cursor");
            case SUBLIME -> List.of("subl", "/usr/local/bin/subl", "/opt/homebrew/bin/subl",
                    "/Applications/Sublime Text.app/Contents/SharedSupport/bin/subl");
            case INTELLIJ -> List.of("idea", TOOLBOX + "idea", "/usr/local/bin/idea", "/opt/homebrew/bin/idea",
                    "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea", "/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea");
            case WEBSTORM -> List.of("webstorm", TOOLBOX + "webstorm", "/usr/local/bin/webstorm", "/opt/homebrew/bin/webstorm",
                    "/Applications/WebStorm.app/Contents/MacOS/webstorm");
            default -> List.of();
        };
        if (WINDOWS) return switch (app) {
            case ZED -> List.of("zed", Path.of(PROGRAMS, "Zed", "bin", "zed.exe").toString(), Path.of(PROGRAMS, "Zed", "Zed.exe").toString());
            case VS_CODE -> List.of("code", Path.of(PROGRAMS, "Microsoft VS Code", "bin", "code.cmd").toString(),
                    Path.of(PROGRAM_FILES, "Microsoft VS Code", "bin", "code.cmd").toString());
            case VSCODIUM -> List.of("codium", Path.of(PROGRAMS, "VSCodium", "bin", "codium.cmd").toString(),
                    Path.of(PROGRAM_FILES, "VSCodium", "bin", "codium.cmd").toString());
            case CURSOR -> List.of("cursor", Path.of(PROGRAMS, "cursor", "resources", "app", "bin", "cursor.cmd").toString());
            case SUBLIME -> List.of("subl", Path.of(PROGRAM_FILES, "Sublime Text", "subl.exe").toString(),
                    Path.of(PROGRAM_FILES, "Sublime Text 3", "subl.exe").toString());
            case INTELLIJ -> List.of("idea", "idea64", TOOLBOX + "idea.cmd");
            case WEBSTORM -> List.of("webstorm", "webstorm64", TOOLBOX + "webstorm.cmd");
            default -> List.of();
        };
        return switch (app) {
            case ZED -> List.of("zed", HOME + "/.local/bin/zed", "zeditor", "zedit", "flatpak:dev.zed.Zed");
            case VS_CODE -> List.of("code", "/snap/bin/code", "/usr/share/code/bin/code", "flatpak:com.visualstudio.code");
            case VSCODIUM -> List.of("codium", "vscodium", "/snap/bin/codium", "flatpak:com.vscodium.codium");
            case CURSOR -> List.of("cursor", HOME + "/.local/bin/cursor", "/opt/cursor/cursor");
            case SUBLIME -> List.of("subl", "/opt/sublime_text/sublime_text", "/snap/bin/subl", "flatpak:com.sublimetext.three");
            case INTELLIJ -> List.of("idea", "intellij-idea-ultimate", "intellij-idea-community", TOOLBOX + "idea",
                    "/snap/bin/intellij-idea-ultimate", "/snap/bin/intellij-idea-community");
            case WEBSTORM -> List.of("webstorm", TOOLBOX + "webstorm", "/snap/bin/webstorm");
            default -> List.of();
        };
    }

    private static @Nullable String onPath(String name) {
        String path = System.getenv("PATH");
        List<String> folders = new ArrayList<>(path == null ? List.of() : List.of(path.split(File.pathSeparator)));
        if (!WINDOWS) folders.addAll(List.of(HOME + "/.local/bin", "/usr/local/bin", "/usr/bin", "/bin"));
        for (String folder : folders) {
            if (folder.isBlank()) continue;
            String found = executable(folder + File.separator + name);
            if (found != null) return found;
        }
        return null;
    }

    private static @Nullable String executable(String candidate) {
        for (String extension : WINDOWS ? WINDOWS_EXTENSIONS : List.of("")) {
            try {
                Path file = Path.of(candidate + extension);
                if (Files.isRegularFile(file) && (WINDOWS || Files.isExecutable(file))) return file.toString();
            } catch (InvalidPathException e) {
                return null;
            }
        }
        return null;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(PREFERENCES)) return;
        try {
            if (!(Json.parse(Files.readString(PREFERENCES)) instanceof Map<?, ?> read)) return;
            if (read.get(COMMAND_KEY) instanceof String text) custom = text;
            if (read.get(CHOICE_KEY) instanceof String id) {
                for (Choice one : Choice.values()) if (one.id.equals(id)) choice = one;
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("{} could not be read", PREFERENCES, e);
        }
    }

    private static void warn(String message) {
        MoudMod.LOG.warn(message);
        Output.add(Output.Level.WARN, "editor", message);
    }
}
