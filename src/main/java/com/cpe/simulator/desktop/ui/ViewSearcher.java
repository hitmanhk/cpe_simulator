package com.cpe.simulator.desktop.ui;

/**
 * A view that can be searched by the global Ctrl+F search bar. Implementations
 * filter/highlight their visible content and report the match count.
 */
public interface ViewSearcher {

    /**
     * Applies the search to this view's content.
     *
     * @return number of matches; 0 also when the query is blank
     */
    int applySearch(String query, SearchOptions options);

    /** Restores the unfiltered view. */
    void clearSearch();
}
