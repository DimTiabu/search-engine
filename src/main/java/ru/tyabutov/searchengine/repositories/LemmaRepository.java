package ru.tyabutov.searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.tyabutov.searchengine.model.LemmaEntity;
import ru.tyabutov.searchengine.model.SiteEntity;

import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma = :lemma AND l.site = :site")
    LemmaEntity findByLemmaAndSiteId(@Param("lemma") String lemma, @Param("site") SiteEntity site);

    @Query("SELECT l FROM LemmaEntity l WHERE l.site = :site")
    List<LemmaEntity> findBySite(@Param("site") SiteEntity site);

    @Transactional
    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.site = :siteId")
    void deleteBySite(@Param("siteId") SiteEntity site);
}
