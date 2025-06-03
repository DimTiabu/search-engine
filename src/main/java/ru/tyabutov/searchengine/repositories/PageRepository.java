package ru.tyabutov.searchengine.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tyabutov.searchengine.model.PageEntity;
import ru.tyabutov.searchengine.model.SiteEntity;

import java.util.List;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    PageEntity findByPath(String path);

    @Query("SELECT p FROM PageEntity p WHERE p.site = :site")
    List<PageEntity> findBySite(@Param("site") SiteEntity site);

    @Modifying
    @Transactional
    @Query("DELETE FROM PageEntity p WHERE p.site.id = :siteId")
    void deleteBySite(@Param("siteId") int siteId);
}
