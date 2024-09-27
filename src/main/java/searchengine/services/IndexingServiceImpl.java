package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserSettings;
import searchengine.model.LemmaCreator;
import searchengine.dto.indexing.SearchResult;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final AtomicBoolean indexingStarting = new AtomicBoolean(false);
    private final AtomicBoolean searchStarting = new AtomicBoolean(false);

    @Autowired
    public IndexingServiceImpl(SitesList sites, UserSettings userSettings,
                               SiteRepository siteRepository, PageRepository pageRepository,
                               LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.sites = sites;
        this.userSettings = userSettings;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public Map<String, Object> startIndexing() {
        if (indexingStarting.get()) return errorResponse(new Exception("Индексация уже запущена"));

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
                synchronized (siteRepository) {
                    siteEntity = siteRepository.findByUrl(url);
                    if (siteEntity != null) {
                        deleteSite(siteEntity);
                    }
                    siteEntity = createSiteEntity(site);
                    siteEntityList.add(siteEntity);
                }
                forkJoinPool.submit(
                        new PageIndexer(siteEntity, url,
                                siteRepository, pageRepository,
                                lemmaRepository, indexRepository,
                                userSettings, indexingStarting));
            }
            forkJoinPool.shutdown();
            forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            for (SiteEntity siteEntity : siteEntityList) {
                if (indexingStarting.get()) synchronized (siteRepository) {
                    siteEntity.setStatus(SiteStatus.INDEXED);
                    System.out.println(siteEntity.getName() + ": Установлен статус INDEXED");
                    siteRepository.save(siteEntity);
                }
            }
        } catch (Exception e) {
            errorResponse(e);
        } finally {
            indexingStarting.set(false);
        }
    }

    @Transactional
    public synchronized void deleteSite(SiteEntity siteEntity) {
        System.out.println("Попытка удаления сайта " + siteEntity.getUrl());
        lemmaRepository.deleteBySite(siteEntity);
        List<PageEntity> pageEntityList
                = pageRepository.findBySite(siteEntity);
        for (PageEntity page : pageEntityList) {
            indexRepository.deleteByPage(page);
        }
        pageRepository.deleteBySite(siteEntity.getId());
        siteRepository.deleteByUrl(siteEntity.getUrl());
        System.out.println("Удален сайт " + siteEntity.getUrl());
    }

    @Transactional
    public void deletePage(PageEntity page) {
        try {
            List<IndexEntity> indexEntityList = indexRepository.findByPage(page);

            for (IndexEntity indexEntity : indexEntityList) {
                int lemmaId = indexEntity.getLemma().getId();
                LemmaEntity lemmaEntity =
                        lemmaRepository.findById(lemmaId).orElse(null);
                if (lemmaEntity == null) continue;
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
            if (!indexingStarting.get()) throw new Exception("Индексация не запущена");
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
            if (indexingStarting.get()) throw new Exception("Индексация уже запущена");

            indexingStarting.set(true);

            PageEntity page = pageRepository.findByPath(url);
            if (page != null) deletePage(page);

            List<Site> sitesList = sites.getSites();
            SiteEntity siteEntity = null;

            for (Site site : sitesList) {
                String siteUrl = site.getUrl();
                if (url.contains(siteUrl)) {
                    siteEntity = siteRepository.findByUrl(siteUrl);
                    if (siteEntity == null) siteEntity = createSiteEntity(site);
                    PageIndexer pageIndexer = new PageIndexer(siteEntity, url,
                            siteRepository, pageRepository,
                            lemmaRepository, indexRepository,
                            userSettings, indexingStarting);
                    Document doc = pageIndexer.getDoc();
                    pageIndexer.createPageWithLemmasAndIndices(doc);
                }
            }
            indexingStarting.set(false);

            if (siteEntity == null) throw new Exception("Данная страница находится " +
                    "за пределами сайтов, указанных в конфигурационном файле");
            return okResponse();
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    public Map<String, Object> search(String query, String site,
                                      int offset, int limit) {
        try {
            if (searchStarting.get()) {
                throw new Exception("Поиск уже запущен");
            } else if (query.isEmpty()) {
                throw new Exception("Задан пустой поисковый запрос");
            }
            searchStarting.set(true);

            List<String> sitesList = new ArrayList<>();
            if (site == null) {
                sites.getSites().forEach(siteToSearch -> sitesList.add(siteToSearch.getUrl()));
            } else {
                sitesList.add(site);
            }

            Set<PageEntity> combinedRelevantPages = new HashSet<>();
            List<LemmaEntity> combinedFilteredLemmas = new ArrayList<>();
            List<String> queryWords = new ArrayList<>();
            Map<PageEntity, SiteEntity> pageToSiteMap = new HashMap<>();

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

            if (combinedFilteredLemmas.isEmpty())
                throw new Exception("Не найдено подходящих лемм для данного запроса");

            if (combinedRelevantPages.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<PageEntity, Double> relevanceMap = new HashMap<>();
            double maxRelevance = 0.0;

            for (PageEntity page : combinedRelevantPages) {
                double relevance;
                if (page != null) {
                    relevance = combinedFilteredLemmas.stream()
                            .mapToDouble(lemma -> {
                                IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemma, page);
                                return indexEntity != null ? indexEntity.getRank() : 0.0;
                            })
                            .sum();
                    relevanceMap.put(page, relevance);
                    if (relevance > maxRelevance) {
                        maxRelevance = relevance;
                    }
                }

            }
            Double finalMaxRelevance = maxRelevance;

            List<SearchResult> results = relevanceMap.entrySet().stream()
                    .map(entry -> {
                        SiteEntity siteEntity = pageToSiteMap.get(entry.getKey());
                        return new SearchResult(
                                siteEntity != null ? siteEntity.getUrl() : "",
                                siteEntity != null ? siteEntity.getName() : "",
                                entry.getKey().getPath(),
                                getPageTitle(entry.getKey()),
                                generateSnippet(entry.getKey(), queryWords),
                                entry.getValue() / finalMaxRelevance);
                    })
                    .sorted(Comparator.comparingDouble(SearchResult::getRelevance).reversed())
                    .collect(Collectors.toList());

            searchStarting.set(false);
            return new HashMap<>() {{
                put("result", "true");
                put("count", String.valueOf(results.size()));
                put("data", results);
            }};
        } catch (Exception e) {
            System.out.println("Ошибка search: " + e.getMessage());
            return errorResponse(new Exception(e.getMessage()));
        } finally {
            searchStarting.set(false);
        }
    }

    private List<LemmaEntity> getFilteredLemmas(String query, SiteEntity siteEntity) {

        LemmaCreator lemmaCreator = new LemmaCreator();
        Map<String, Integer> words = lemmaCreator.getLemmas(query);

        return words.keySet().stream()
                .map(lemma -> lemmaRepository.findByLemmaAndSiteId(lemma, siteEntity))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LemmaEntity::getFrequency))
                .toList();
    }

    private Set<PageEntity> getRelevantPages(List<LemmaEntity> filteredLemmas, List<String> queryWords) {
        try {
            if (filteredLemmas.isEmpty()) return null;
            for (LemmaEntity lemma : filteredLemmas) {
                String word = lemma.getLemma();
                queryWords.add(word);
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
        try {
            int snippetLength = 200;

            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            LemmaCreator lemmaCreator = new LemmaCreator();

            String[] newWords = text.split("\\s+");
            String regex = "[^а-я]";
            boolean firstWordIsAppend = false;

            for (int i = 0; i < newWords.length; i++) {
                String word = newWords[i];
                String changedWord = word.toLowerCase().replaceAll(regex, "");

                if (changedWord.isEmpty()) continue;
                String lemma = lemmaCreator.takeLemmaFromWord(changedWord, luceneMorph);
                if (queryWords.contains(lemma)) {
                    word = "<b>".concat(word).concat("</b>");
                    if (String.valueOf(snippet).isEmpty()) {
                        snippet.append(word).append(" ");
                        firstWordIsAppend = true;
                        continue;
                    }
                }
                int remainingLength = snippetLength - word.length();
                if (firstWordIsAppend && (remainingLength) >= 0) {
                    snippet.append(word).append(" ");
                    snippetLength = remainingLength;
                }
                if (remainingLength <= 0) break;
            }

        } catch (IOException e) {
            System.out.println("Ошибка snippet " + e.getMessage());
        }
        return String.valueOf(snippet);
    }

    private String getPageTitle(PageEntity page) {
        String title = "";
        try {
            Thread.sleep(150);
            Document doc = Jsoup.connect(page.getPath())
                    .userAgent(userSettings.getUser())
                    .referrer(userSettings.getReferrer())
                    .get();
            Element element = doc.select("title").first();
            title = element.text();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return title;
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
