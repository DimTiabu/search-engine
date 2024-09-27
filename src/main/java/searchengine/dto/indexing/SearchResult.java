package searchengine.dto.indexing;

import lombok.Data;
import searchengine.config.Site;

@Data
public class SearchResult {
    private final String site;
    private final String siteName;
    private final String uri;
    private final String title;
    private final String snippet;
    private final double relevance;
}
