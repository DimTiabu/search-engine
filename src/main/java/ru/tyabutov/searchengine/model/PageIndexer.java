package ru.tyabutov.searchengine.model;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import ru.tyabutov.searchengine.config.UserSettings;
import ru.tyabutov.searchengine.repositories.IndexRepository;
import ru.tyabutov.searchengine.repositories.LemmaRepository;
import ru.tyabutov.searchengine.repositories.PageRepository;
import ru.tyabutov.searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;


@RequiredArgsConstructor
public class PageIndexer extends RecursiveTask<Void> {
    private final SiteEntity site;
    private final String path;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final UserSettings userSettings;
    private final AtomicBoolean running;
    private final LemmaCreator lemmaCreator;

    private final List<PageIndexer> tasks = new ArrayList<>();

    @Override
    protected Void compute() {
        try {
            if (!running.get()) {
                return null;
            }

            Document doc = getDoc();
            if (doc == null) {
                return null;
            }

            createPageWithLemmasAndIndices(doc);

            Elements links = doc.select("a");

            for (Element link : links) {
                String url = link.absUrl("href");
                PageEntity page = pageRepository.findByPath(url);
                if (!url.equals(path) &&
                        url.contains(path) &&
                        !url.contains("#") &&
                        page == null) {
                    PageIndexer task = new PageIndexer(site, url, siteRepository, pageRepository,
                            lemmaRepository, indexRepository, userSettings, running, lemmaCreator);
                    task.fork();
                    tasks.add(task);
                }
            }
            invokeAll(tasks);
        } catch (Exception e) {
            System.out.println("Ошибка compute: " + e.getMessage() + " для страницы " + path);
        }
        for (PageIndexer task : tasks) task.join();
        return null;
    }


    public Document getDoc() {
        try {
            int delay = 500 + (int) (Math.random() * 4500);
            Thread.sleep(delay);
            return Jsoup.connect(path)
                    .userAgent(userSettings.getUser())
                    .referrer(userSettings.getReferrer())
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .get();
        } catch (Exception e) {
            System.out.println("Ошибка getDoc: " + e.getMessage() + " для страницы " + path);
        }
        return null;
    }

    public void createPageWithLemmasAndIndices(Document doc) {
        updateStatusTime();
        PageEntity page = createPage(doc);
        if (page != null && page.getCode() < 400) {
            createLemmasAndIndices(page);
        }
    }

    @Transactional
    public void updateStatusTime() {
        if (running.get()) {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    @Transactional
    public PageEntity createPage(Document doc) {
        try {
            PageEntity page = new PageEntity();
            page.setSite(site);
            page.setPath(path);
            page.setContent(doc.html());
            page.setCode(doc.connection().response().statusCode());
            page.setTitle(doc.title());
            if (pageRepository.findByPath(path) == null) {
                pageRepository.save(page);
                return page;
            }
        } catch (Exception e) {
            System.out.println("Ошибка createPage: " + e.getMessage() + " для страницы " + path);
            return null;
        }
        return null;
    }

    @Transactional
    public void createLemmasAndIndices(PageEntity page) {
        try {
            SiteEntity site = page.getSite();
            Map<String, Integer> lemmas = lemmaCreator.getLemmas(page.getContent());
            List<IndexEntity> indicesToSave = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                float count = entry.getValue();
                LemmaEntity lemma = lemmaRepository.findByLemmaAndSiteId(lemmaText, site);
                if (lemma == null) {
                    lemma = new LemmaEntity();
                    lemma.setSite(site);
                    lemma.setLemma(lemmaText);
                    lemma.setFrequency(1);
                } else {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                }
                lemmaRepository.save(lemma);
                findOrCreateIndex(lemma, page, count, indicesToSave);
            }
            indexRepository.saveAll(indicesToSave);

        } catch (Exception e) {
            System.out.println("Ошибка createLemma: " + e.getMessage());
        }
    }

    private void findOrCreateIndex(LemmaEntity lemma, PageEntity page,
                                   float count, List<IndexEntity> indicesToSave) {
        IndexEntity index = indexRepository.findByLemmaIdAndPageId(lemma, page);
        if (index == null) {
            index = new IndexEntity();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            indicesToSave.add(index);
        }
    }
}
