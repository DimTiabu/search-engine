package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.*;

@Data
@AllArgsConstructor
public class SearchContext {
    Set<PageEntity> combinedRelevantPages;
    List<LemmaEntity> combinedFilteredLemmas;
    List<String> queryWords;
    Map<PageEntity, SiteEntity> pageToSiteMap;
}
