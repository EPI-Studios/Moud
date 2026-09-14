package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Api {

    public enum Kind { METHOD, FUNCTION, FIELD }

    public record Member(String name, Kind kind, String type) {}

    public record Decl(String name, String parent, List<Member> members) {}

    private final Map<String, Decl> classes = new LinkedHashMap<>();
    private final Map<String, String> globals = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> extensions = new LinkedHashMap<>();

    public void declare(Decl decl) {
        Decl existing = classes.get(decl.name());
        if (existing == null) {
            classes.put(decl.name(), decl);
            return;
        }
        List<Member> merged = new ArrayList<>(existing.members());
        for (Member member : decl.members()) {
            merged.removeIf(one -> one.name().equals(member.name()));
            merged.add(member);
        }
        classes.put(decl.name(), new Decl(decl.name(), decl.parent() != null ? decl.parent() : existing.parent(), merged));
    }

    public void global(String name, String type) {
        globals.put(name, type);
    }

    public void extension(String global, String type) {
        extensions.put(global, type);
    }

    public Map<String, String> extensions() {
        return extensions;
    }

    public void alias(String name, String type) {
        aliases.put(name, type);
    }

    public Collection<Decl> classes() {
        return classes.values();
    }

    public Decl decl(String name) {
        return classes.get(name);
    }

    public Map<String, String> globals() {
        return globals;
    }

    public Map<String, String> aliases() {
        return aliases;
    }
}
