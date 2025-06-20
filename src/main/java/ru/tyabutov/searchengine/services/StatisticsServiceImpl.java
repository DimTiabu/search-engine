package ru.tyabutov.searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.tyabutov.searchengine.config.Site;
import ru.tyabutov.searchengine.config.SitesList;
import ru.tyabutov.searchengine.dto.statistics.DetailedStatisticsItem;
import ru.tyabutov.searchengine.dto.statistics.StatisticsData;
import ru.tyabutov.searchengine.dto.statistics.StatisticsResponse;
import ru.tyabutov.searchengine.dto.statistics.TotalStatistics;
import ru.tyabutov.searchengine.model.LemmaEntity;
import ru.tyabutov.searchengine.model.PageEntity;
import ru.tyabutov.searchengine.model.SiteEntity;
import ru.tyabutov.searchengine.repositories.LemmaRepository;
import ru.tyabutov.searchengine.repositories.PageRepository;
import ru.tyabutov.searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<Site> sitesList = sites.getSites();

        List<DetailedStatisticsItem> detailed = sitesList.stream()
                .map(site -> createItem(site, total))
                .toList();
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem createItem(Site site, TotalStatistics total) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        SiteEntity siteEntity;
        if (siteRepository.findByUrl(site.getUrl()) == null) {
            item.setPages(0);
            item.setLemmas(0);
            item.setStatus(null);
            item.setError(null);
            item.setStatusTime(0);
        } else {
            siteEntity = siteRepository.findByUrl(site.getUrl());
            List<PageEntity> pageEntityList = pageRepository.findBySite(siteEntity);
            int pages = pageEntityList.size();
            item.setPages(pages);

            List<LemmaEntity> lemmaEntityList = lemmaRepository.findBySite(siteEntity);
            int lemmas = lemmaEntityList.size();
            item.setLemmas(lemmas);

            item.setStatus(siteEntity.getStatus().toString());
            item.setError(siteEntity.getLastError());

            LocalDateTime localDateTime = siteEntity.getStatusTime();
            ZoneId zoneId = ZoneId.systemDefault();
            Instant instant = localDateTime.atZone(zoneId).toInstant();

            long millis = instant.toEpochMilli();
            item.setStatusTime(millis);

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
        }
        return item;
    }
}
