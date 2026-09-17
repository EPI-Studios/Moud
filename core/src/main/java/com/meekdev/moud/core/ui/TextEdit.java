package com.meekdev.moud.core.ui;

public record TextEdit(String text, int caret, int anchor) {

    public TextEdit {
        caret = Math.clamp(caret, 0, text.length());
        anchor = Math.clamp(anchor, 0, text.length());
    }

    public static TextEdit at(String text, int caret) {
        return new TextEdit(text, caret, caret);
    }

    public boolean hasSelection() {
        return caret != anchor;
    }

    public int start() {
        return Math.min(caret, anchor);
    }

    public int end() {
        return Math.max(caret, anchor);
    }

    public String selected() {
        return text.substring(start(), end());
    }

    public TextEdit insert(String typed, boolean multiLine) {
        String clean = multiLine ? typed.replace("\r\n", "\n").replace('\r', '\n') : typed.replace("\r", "").replace("\n", " ");
        String next = text.substring(0, start()) + clean + text.substring(end());
        return at(next, start() + clean.length());
    }

    public TextEdit backspace(boolean word) {
        if (hasSelection()) return insert("", true);
        int from = word ? wordLeft(caret) : previous(caret);
        return at(text.substring(0, from) + text.substring(caret), from);
    }

    public TextEdit delete(boolean word) {
        if (hasSelection()) return insert("", true);
        int to = word ? wordRight(caret) : next(caret);
        return at(text.substring(0, caret) + text.substring(to), caret);
    }

    public TextEdit left(boolean word, boolean select) {
        if (hasSelection() && !select) return at(text, start());
        return move(word ? wordLeft(caret) : previous(caret), select);
    }

    public TextEdit right(boolean word, boolean select) {
        if (hasSelection() && !select) return at(text, end());
        return move(word ? wordRight(caret) : next(caret), select);
    }

    public TextEdit home(boolean select) {
        return move(text.lastIndexOf('\n', caret - 1) + 1, select);
    }

    public TextEdit end(boolean select) {
        int stop = text.indexOf('\n', caret);
        return move(stop < 0 ? text.length() : stop, select);
    }

    public TextEdit up(boolean select) {
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        if (lineStart == 0) return move(0, select);
        int column = caret - lineStart;
        int previousStart = text.lastIndexOf('\n', lineStart - 2) + 1;
        return move(Math.min(previousStart + column, lineStart - 1), select);
    }

    public TextEdit down(boolean select) {
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = text.indexOf('\n', caret);
        if (lineEnd < 0) return move(text.length(), select);
        int column = caret - lineStart;
        int nextEnd = text.indexOf('\n', lineEnd + 1);
        return move(Math.min(lineEnd + 1 + column, nextEnd < 0 ? text.length() : nextEnd), select);
    }

    public TextEdit all() {
        return new TextEdit(text, text.length(), 0);
    }

    public TextEdit move(int to, boolean select) {
        return new TextEdit(text, to, select ? anchor : to);
    }

    public TextEdit word(int around) {
        int from = around;
        int to = around;
        while (from > 0 && wordy(text.charAt(from - 1))) from--;
        while (to < text.length() && wordy(text.charAt(to))) to++;
        return new TextEdit(text, to, from);
    }

    private int previous(int at) {
        return at <= 0 ? 0 : Character.offsetByCodePoints(text, at, -1);
    }

    private int next(int at) {
        return at >= text.length() ? text.length() : Character.offsetByCodePoints(text, at, 1);
    }

    private int wordLeft(int at) {
        int n = at;
        while (n > 0 && !wordy(text.charAt(n - 1))) n--;
        while (n > 0 && wordy(text.charAt(n - 1))) n--;
        return n;
    }

    private int wordRight(int at) {
        int n = at;
        while (n < text.length() && !wordy(text.charAt(n))) n++;
        while (n < text.length() && wordy(text.charAt(n))) n++;
        return n;
    }

    private static boolean wordy(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
