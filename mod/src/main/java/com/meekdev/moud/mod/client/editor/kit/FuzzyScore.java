package com.meekdev.moud.mod.client.editor.kit;

import java.util.Locale;
import java.util.OptionalInt;

public final class FuzzyScore {

    public static final int NO_MATCH = Integer.MIN_VALUE;

    private static final int WORD_START_BONUS = 8;
    private static final int ADJACENT_BONUS = 5;
    private static final int LEADING_PENALTY_LIMIT = 12;

    private FuzzyScore() {
    }

    public static int of(String candidate, String query) {
        String haystack = candidate.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int score = 0;
        int cursor = 0;
        int previousMatch = -2;
        for (int index = 0; index < needle.length(); index++) {
            OptionalInt found = indexOf(haystack, needle.charAt(index), cursor);
            if (found.isEmpty()) {
                return NO_MATCH;
            }
            int position = found.getAsInt();
            score += positionScore(haystack, position, previousMatch);
            previousMatch = position;
            cursor = position + 1;
        }
        return score - Math.min(previousMatch, LEADING_PENALTY_LIMIT);
    }

    private static int positionScore(String haystack, int position, int previousMatch) {
        if (position == previousMatch + 1) {
            return ADJACENT_BONUS;
        }
        if (position == 0 || !Character.isLetterOrDigit(haystack.charAt(position - 1))) {
            return WORD_START_BONUS;
        }
        return 1;
    }

    private static OptionalInt indexOf(String haystack, char character, int from) {
        int found = haystack.indexOf(character, from);
        return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
    }
}
