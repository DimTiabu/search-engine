package ru.tyabutov.searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tyabutov.searchengine.config.Site;
import ru.tyabutov.searchengine.config.SitesList;
import ru.tyabutov.searchengine.config.UserSettings;
import ru.tyabutov.searchengine.dto.indexing.SearchContext;
import ru.tyabutov.searchengine.model.*;
import ru.tyabutov.searchengine.dto.indexing.SearchResult;
import ru.tyabutov.searchengine.model.*;
import ru.tyabutov.searchengine.repositories.IndexRepository;
import ru.tyabutov.searchengine.repositories.LemmaRepository;
import ru.tyabutov.searchengine.repositories.PageRepository;
import ru.tyabutov.searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final UserSettings userSettings;
    private volatile SiteRepository siteRepository;
    private volatile PageRepository pageRepository;
    private volatile LemmaRepository lemmaRepository;
    private volatile IndexRepository indexRepository;
    private LuceneMorphology luceneMorph = new RussianLuceneMorphology();
    private LemmaCreator lemmaCreator = new LemmaCreator(luceneMorph);


    private final AtomicBoolean indexingStarting = new AtomicBoolean(false);
    private final AtomicBoolean searchStarting = new AtomicBoolean(false);
    private static final int SNIPPET_LENGTH = 200;

    @Autowired
    public IndexingServiceImpl(SitesList sites, UserSettings userSettings,
                               SiteRepository siteRepository, PageRepository pageRepository,
                               LemmaRepository lemmaRepository, IndexRepository indexRepository) throws IOException {
        this.sites = sites;
        this.userSettings = userSettings;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public Map<String, Object> startIndexing() {
        if (indexingStarting.get()) {
            return errorResponse(new Exception("Индексация уже запущена"));
        }

        indexingStarting.set(true);
        new Thread(this::indexingTask).start();

        return okResponse();
    }

    private void indexingTask() {
        try {
            List<Site> sitesList = sites.getSites();
            List<SiteEntity> siteEntityList = new ArrayList<>();
            ForkJoinPool forkJoinPool = new ForkJoinPool();

            for (Site site : sitesList) {
                SiteEntity siteEntity;
                String url = site.getUrl();
                siteEntity = updateSite(site, url, siteEntityList);
                forkJoinPool.submit(
                        new PageIndexer(siteEntity, url, siteRepository, pageRepository,
                                lemmaRepository, indexRepository, userSettings, indexingStarting, lemmaCreator));
            }
            forkJoinPool.shutdown();
            forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            for (SiteEntity siteEntity : siteEntityList) {
                if (indexingStarting.get()) {
                    siteEntity.setStatus(SiteStatus.INDEXED);
                    siteRepository.save(siteEntity);
                }
            }
        } catch (Exception e) {
            errorResponse(e);
        } finally {
            indexingStarting.set(false);
        }
    }

    private SiteEntity updateSite(Site site, String url, List<SiteEntity> siteEntityList) {
        SiteEntity siteEntity = siteRepository.findByUrl(url);
        if (siteEntity != null) {
            deleteSite(siteEntity);
        }
        siteEntity = createSiteEntity(site);
        siteEntityList.add(siteEntity);
        return siteEntity;
    }

    @Transactional
    public void deleteSite(SiteEntity siteEntity) {
        List<PageEntity> pageEntityList
                = pageRepository.findBySite(siteEntity);
        for (PageEntity page : pageEntityList) {
            indexRepository.deleteByPage(page);
        }
        lemmaRepository.deleteBySite(siteEntity);
        pageRepository.deleteBySite(siteEntity.getId());
        siteRepository.deleteByUrl(siteEntity.getUrl());
    }

    @Transactional
    public void deletePage(PageEntity page) {
        try {
            List<IndexEntity> indexEntityList = indexRepository.findByPage(page);

            for (IndexEntity indexEntity : indexEntityList) {
                int lemmaId = indexEntity.getLemma().getId();
                LemmaEntity lemmaEntity =
                        lemmaRepository.findById(lemmaId).orElse(null);
                if (lemmaEntity == null) {
                    continue;
                }
                int frequency = lemmaEntity.getFrequency();
                if (frequency == 1) {
                    lemmaRepository.deleteById(lemmaId);
                } else {
                    lemmaEntity.setFrequency(frequency - 1);
                    lemmaRepository.save(lemmaEntity);
                }
            }

            indexRepository.deleteByPage(page);
            pageRepository.delete(page);

        } catch (Exception e) {
            System.out.println("Ошибка deletePage: " + e.getMessage());
        }
    }

    @Transactional
    public SiteEntity createSiteEntity(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    @Transactional
    public Map<String, Object> stopIndexing() {
        try {
            if (!indexingStarting.get()) {
                throw new Exception("Индексация не запущена");
            }
            siteRepository.findByStatus(SiteStatus.INDEXING)
                    .forEach(site -> {
                        site.setStatus(SiteStatus.FAILED);
                        site.setLastError("Индексация остановлена пользователем");
                        siteRepository.save(site);
                    });
            indexingStarting.set(false);
            return okResponse();
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Transactional
    public Map<String, Object> indexPage(String url) {
        try {
            if (indexingStarting.get()) {
                throw new Exception("Индексация уже запущена");
            }

            indexingStarting.set(true);

            PageEntity page = pageRepository.findByPath(url);
            if (page != null) {
                deletePage(page);
            }

            List<Site> sitesList = sites.getSites();
            SiteEntity siteEntity = null;

            for (Site site : sitesList) {
                String siteUrl = site.getUrl();
                if (url.contains(siteUrl)) {
                    siteEntity = siteRepository.findByUrl(siteUrl);
                    if (siteEntity == null) {
                        siteEntity = createSiteEntity(site);
                    }
                    PageIndexer pageIndexer = new PageIndexer(siteEntity, url, siteRepository, pageRepository,
                            lemmaRepository, indexRepository, userSettings, indexingStarting, lemmaCreator);
                    Document doc = pageIndexer.getDoc();
                    pageIndexer.createPageWithLemmasAndIndices(doc);
                }
            }
            indexingStarting.set(false);

            if (siteEntity == null) {
                throw new Exception("Данная страница находится " +
                        "за пределами сайтов, указанных в конфигурационном файле");
            }
            return okResponse();
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    public Map<String, Object> search(String query, String site,
                                      int offset, int limit) {
        try {
            validateSearch(query);
            searchStarting.set(true);

            List<String> sitesList = generateSitesList(site);

            SearchContext searchContext = initializeSearchContext(query, sitesList);

            if (searchContext.getCombinedFilteredLemmas().isEmpty()) {
                throw new Exception("Не найдено подходящих лемм для данного запроса");
            }

            if (searchContext.getCombinedRelevantPages().isEmpty()) {
                return Collections.emptyMap();
            }

            return prepareSearchResult(searchContext, offset, limit);

        } catch (Exception e) {
            return errorResponse(new Exception(e.getMessage()));
        } finally {
            searchStarting.set(false);
        }
    }

    private void validateSearch(String query) throws Exception {
        if (searchStarting.get()) {
            throw new Exception("Поиск уже запущен");
        } else if (query.isEmpty()) {
            throw new Exception("Задан пустой поисковый запрос");
        }
    }

    private List<String> generateSitesList(String site) {
        List<String> sitesList = new ArrayList<>();
        if (site == null) {
            sites.getSites().forEach(siteToSearch -> sitesList.add(siteToSearch.getUrl()));
        } else {
            sitesList.add(site);
        }
        return sitesList;
    }

    private SearchContext initializeSearchContext(String query, List<String> sitesList) {
        Set<PageEntity> combinedRelevantPages = new HashSet<>();
        List<LemmaEntity> combinedFilteredLemmas = new ArrayList<>();
        List<String> queryWords = new ArrayList<>();
        Map<PageEntity, SiteEntity> pageToSiteMap = new HashMap<>();

        updateCombinedRelevantPages(sitesList, query, queryWords,
                combinedRelevantPages, combinedFilteredLemmas, pageToSiteMap);

        return new SearchContext(combinedRelevantPages, combinedFilteredLemmas, queryWords, pageToSiteMap);
    }

    private void updateCombinedRelevantPages(List<String> sitesList, String query, List<String> queryWords,
                                             Set<PageEntity> combinedRelevantPages,
                                             List<LemmaEntity> combinedFilteredLemmas,
                                             Map<PageEntity, SiteEntity> pageToSiteMap) {

        for (String siteUrl : sitesList) {
            SiteEntity siteEntity = siteRepository.findByUrl(siteUrl);

            List<LemmaEntity> filteredLemmas = getFilteredLemmas(query, siteEntity);
            combinedFilteredLemmas.addAll(filteredLemmas);

            Set<PageEntity> relevantPages = getRelevantPages(filteredLemmas, queryWords);

            if (relevantPages != null) {
                combinedRelevantPages.addAll(relevantPages);
                relevantPages.forEach(page -> pageToSiteMap.put(page, siteEntity));
            }
        }
    }

    private Map<String, Object> prepareSearchResult(SearchContext searchContext, int offset, int limit) {
        Map<PageEntity, Double> relevanceMap =
                calculateRelevance(searchContext.getCombinedRelevantPages(), searchContext.getCombinedFilteredLemmas());
        double maxRelevance = relevanceMap.values().stream().max(Double::compareTo).orElse(0.0);

        List<SearchResult> results = relevanceMap.entrySet().stream()
                .parallel()
                .map(entry -> createSearchResult(entry, searchContext.getQueryWords(),
                        searchContext.getPageToSiteMap(), maxRelevance))
                .sorted(Comparator.comparingDouble(SearchResult::getRelevance).reversed())
                .collect(Collectors.toList());
        int count = results.size();

        List<SearchResult> newResults = results.subList(offset, Math.min(offset + limit, count));

        return new HashMap<>() {{
            put("result", "true");
            put("count", String.valueOf(count));
            put("data", count <= limit ? results : newResults);
        }};
    }

    private Map<PageEntity, Double> calculateRelevance(Set<PageEntity> combinedRelevantPages,
                                                       List<LemmaEntity> combinedFilteredLemmas) {
        Map<PageEntity, Double> relevanceMap = new HashMap<>();

        for (PageEntity page : combinedRelevantPages) {
            double pageRelevance = combinedFilteredLemmas.stream()
                    .mapToDouble(lemma -> {
                        IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemma, page);
                        return indexEntity != null ? indexEntity.getRank() : 0.0;
                    })
                    .sum();
            relevanceMap.put(page, pageRelevance);
        }
        return relevanceMap;
    }

    private SearchResult createSearchResult(Map.Entry<PageEntity, Double> entry, List<String> queryWords,
                                            Map<PageEntity, SiteEntity> pageToSiteMap, double maxRelevance) {
        SiteEntity siteEntity = pageToSiteMap.get(entry.getKey());
        double normalizedRelevance = entry.getValue() / maxRelevance;
        return new SearchResult(
                siteEntity != null ? siteEntity.getUrl() : "",
                siteEntity != null ? siteEntity.getName() : "",
                entry.getKey().getPath().replaceAll(siteEntity != null ? siteEntity.getUrl() : "", ""),
                entry.getKey().getTitle(),
                generateSnippet(entry.getKey(), queryWords),
                normalizedRelevance
        );
    }

    private List<LemmaEntity> getFilteredLemmas(String query, SiteEntity siteEntity) {

        Map<String, Integer> words = lemmaCreator.getLemmas(query);

        return words.keySet().stream()
                .parallel()
                .map(lemma -> lemmaRepository.findByLemmaAndSiteId(lemma, siteEntity))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LemmaEntity::getFrequency))
                .toList();
    }

    private Set<PageEntity> getRelevantPages(List<LemmaEntity> filteredLemmas, List<String> queryWords) {
        try {
            if (filteredLemmas.isEmpty()) {
                return null;
            }
            for (LemmaEntity lemma : filteredLemmas) {
                queryWords.add(lemma.getLemma());
            }

            Set<PageEntity> relevantPages =
                    indexRepository.findByLemma(filteredLemmas.get(0))
                            .stream().filter(Objects::nonNull)
                            .map(IndexEntity::getPage)
                            .collect(Collectors.toSet());
            for (LemmaEntity filteredLemma : filteredLemmas) {
                if (filteredLemma != null) {
                    Set<PageEntity> pagesWithLemma = indexRepository.findByLemma(filteredLemma)
                            .stream()
                            .map(IndexEntity::getPage)
                            .collect(Collectors.toSet());

                    relevantPages.retainAll(pagesWithLemma);
                }
            }
            return relevantPages;
        } catch (Exception e) {
            System.out.println("Ошибка getRelevantPages " + e.getMessage());
        }
        return null;
    }

    private String generateSnippet(PageEntity page, List<String> queryWords) {
        String text = Jsoup.parse(page.getContent()).text();
        StringBuilder snippet = new StringBuilder();
        int snippetLength = SNIPPET_LENGTH;

        String[] words = text.split("\\s+");
        boolean isFirstWordAppended = false;

        String regex = "[^а-я]";

        for (String word : words) {
            String changedWord = word.toLowerCase().replaceAll(regex, "");

            if (changedWord.isEmpty()) {
                continue;
            }
            String lemma = lemmaCreator.takeLemmaFromWord(changedWord);
            if (queryWords.contains(lemma)) {
                if (snippet.isEmpty()) {
                    isFirstWordAppended = true;
                }
                word = "<b>".concat(word).concat("</b>");
            }
            if (isFirstWordAppended) {
                int remainingLength = snippetLength - word.length();
                if (remainingLength <= 0) {
                    break;
                }
                snippet.append(word).append(" ");
                snippetLength = remainingLength;
            }
        }
        return String.valueOf(snippet);
    }

    public Map<String, Object> okResponse() {
        return new HashMap<>() {{
            put("result", "true");
        }};
    }

    public Map<String, Object> errorResponse(Exception exception) {
        return new HashMap<>() {{
            put("result", "false");
            put("message", exception.getMessage());
        }};
    }
}