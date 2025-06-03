package ru.tyabutov.searchengine.services;

import java.util.Map;

public interface IndexingService {

    Map<String, Object> startIndexing();
    Map<String, Object> stopIndexing();
    Map<String, Object> indexPage(String url);
    Map<String, Object> search(String query, String site, int offset, int limit);
}
