package com.meekdev.moud.mod.client.editor.files;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class UserPaths {

    private static final String INVALID = "/\\:*?\"<>|";
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private UserPaths() {}

    public static Optional<Path> parse(String typed) {
        String text = typed.strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1).strip();
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> nameProblem(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return Optional.of("That is not a file name");
        if (name.chars().anyMatch(c -> c < ' ' || INVALID.indexOf(c) >= 0)) return Optional.of("A name can not contain / \\ : * ? \" < > |");
        if (name.endsWith(".") || name.endsWith(" ")) return Optional.of("A name can not end with a dot or a space");
        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).strip().toLowerCase(Locale.ROOT);
        if (RESERVED.contains(stem)) return Optional.of(name + " is a name Windows keeps for itself");
        return Optional.empty();
    }
}
