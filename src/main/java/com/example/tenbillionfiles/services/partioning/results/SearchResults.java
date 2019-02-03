package com.example.tenbillionfiles.services.partioning.results;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Result of Strings
 */
public class SearchResults implements ConsolidatedResult<List<String>> {

    private final List<String> consolidatedResult = new LinkedList<>();

    @Override
    public void addResult(final List<String> results) {
        consolidatedResult.addAll(results);
    }

    public List<String> getResults() {
        return Collections.unmodifiableList(consolidatedResult);
    }
}