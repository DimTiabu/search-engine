package ru.tyabutov.searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ru.tyabutov.searchengine.model.LemmaEntity;
import ru.tyabutov.searchengine.model.PageEntity;
import ru.tyabutov.searchengine.model.SiteEntity;

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
