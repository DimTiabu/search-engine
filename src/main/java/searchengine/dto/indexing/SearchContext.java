package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.*;

@Getter
@Setter
@AllArgsConstructor
public class SearchContext {
    Set<PageEntity> combinedRelevantPages;
    List<LemmaEntity> combinedFilteredLemmas;
    List<String> queryWords;
    Map<PageEntity, SiteEntity> pageToSiteMap;
}
