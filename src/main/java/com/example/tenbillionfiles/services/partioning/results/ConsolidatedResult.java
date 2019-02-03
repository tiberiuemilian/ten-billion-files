package com.example.tenbillionfiles.services.partioning.results;

/**
 * Results of all the completed futures.
 */
public interface ConsolidatedResult<T> {

    void addResult(T result);
}