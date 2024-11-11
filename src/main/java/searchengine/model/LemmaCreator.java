package searchengine.model;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LemmaCreator {
    private LuceneMorphology luceneMorph;

    public LemmaCreator(LuceneMorphology luceneMorph) {
        this.luceneMorph = luceneMorph;
    }

    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new ConcurrentHashMap<>();
        Map<String, String> wordCache = new ConcurrentHashMap<>();

        Arrays.stream(takeWordsFromText(text))
                .parallel()
                .forEach(word -> {
                    if (word.isEmpty() || wordCache.containsKey(word)) {
                        return;
                    }
                    List<String> wordMorphInfo = luceneMorph.getMorphInfo(word)
                            .stream()
                            .filter(w -> !w.contains("СОЮЗ") &&
                                    !w.contains("ПРЕДЛ") &&
                                    !w.contains("ЧАСТ") &&
                                    !w.contains("МЕЖД"))
                            .toList();

                    if (!wordMorphInfo.isEmpty()) {
                        wordMorphInfo
                                .forEach(w -> {
                                    String lemma = takeLemmaFromWord(word);
                                    if (lemma != null) {
                                        lemmas.merge(lemma, 1, Integer::sum);
                                        wordCache.put(word, lemma);
                                    }
                                });
                    }
                });

        return lemmas;
    }

    public String[] takeWordsFromText(String text) {
        text = text.toLowerCase();

        String regex = "[^а-я]";
        return text.replaceAll(regex, " ")
                .split("\\s+");
    }


    public String takeLemmaFromWord(String word) {
        List<String> wordBaseForms = luceneMorph.getNormalForms(word);
        return !wordBaseForms.isEmpty() ? wordBaseForms.get(0) : null;
    }
}
