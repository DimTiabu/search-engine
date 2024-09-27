package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    String[] statuses = {"INDEXED", "FAILED", "INDEXING"};
    String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            ""
    };

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();

        for (int i = 0; i < sitesList.size(); i++) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            Site site = sitesList.get(i);
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
                long seconds = instant.getEpochSecond();
                item.setStatusTime(millis);
                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + lemmas);
            }
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
