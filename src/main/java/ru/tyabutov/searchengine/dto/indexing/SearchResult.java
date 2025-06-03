package ru.tyabutov.searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SearchResult {
    private final String site;
    private final String siteName;
    private final String uri;
    private final String title;
    private final String snippet;
    private final double relevance;
}
