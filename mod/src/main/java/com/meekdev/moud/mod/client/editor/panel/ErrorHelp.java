package com.meekdev.moud.mod.client.editor.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.moud.mod.MoudMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ErrorHelp {

    private static final String RULES_FILE = "/assets/moud/editor/error-help.json";
    private static final Pattern GROUP = Pattern.compile("\\{(\\d+)}");

    record Explained(String title, String why, String fix) {}

    private record Rule(Pattern pattern, String title, String why, String fix) {}

    private static final List<Rule> RULES = loadRules();

    private ErrorHelp() {}

    static Explained explain(String message) {
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(message);
            if (matcher.find()) return new Explained(fill(rule.title(), matcher), fill(rule.why(), matcher), fill(rule.fix(), matcher));
        }
        String first = message.lines().findFirst().orElse(message);
        int colon = first.lastIndexOf(": ");
        return new Explained(colon >= 0 && colon < first.length() - 2 ? first.substring(colon + 2) : first, "", "");
    }

    private static String fill(String text, Matcher found) {
        return GROUP.matcher(text).replaceAll(slot -> {
            String value = String.valueOf(found.group(Integer.parseInt(slot.group(1))));
            return Matcher.quoteReplacement(value);
        });
    }

    private static List<Rule> loadRules() {
        List<Rule> rules = new ArrayList<>();
        try (InputStream in = ErrorHelp.class.getResourceAsStream(RULES_FILE)) {
            if (in == null) throw new IOException(RULES_FILE + " is missing");
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject rule = element.getAsJsonObject();
                rules.add(new Rule(
                        Pattern.compile(rule.get("pattern").getAsString()),
                        rule.get("title").getAsString(),
                        rule.get("why").getAsString(),
                        rule.get("fix").getAsString()));
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("error help rules could not be read", e);
        }
        return rules;
    }
}
