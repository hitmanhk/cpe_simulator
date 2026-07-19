package com.cpe.simulator.desktop.ui;

import java.util.regex.Pattern;

/**
 * Search options for the Ctrl+F in-page search: case sensitivity and whole
 * word matching. Cross-page search is controlled by the search bar itself,
 * not part of this record.
 */
public record SearchOptions(boolean caseSensitive, boolean wholeWord) {

    public static final SearchOptions DEFAULT = new SearchOptions(false, false);

    /** Compiles the query into a Pattern honoring both flags. */
    public Pattern toPattern(String query) {
        String q = Pattern.quote(query);
        if (wholeWord) {
            q = "\\b" + q + "\\b";
        }
        int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return Pattern.compile(q, flags);
    }

    /** Convenience match used by views filtering rows/nodes. */
    public boolean matches(String query, String text) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        if (text == null) {
            return false;
        }
        return toPattern(query).matcher(text).find();
    }
}
