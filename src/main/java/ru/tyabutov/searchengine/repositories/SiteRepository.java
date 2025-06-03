package ru.tyabutov.searchengine.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.tyabutov.searchengine.model.SiteEntity;
import ru.tyabutov.searchengine.model.SiteStatus;

import java.util.List;

public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Query("SELECT s FROM SiteEntity s WHERE s.url = :url")
    SiteEntity findByUrl(String url);

    List<SiteEntity> findByStatus(SiteStatus status);
    @Transactional
    @Modifying
    @Query("DELETE FROM SiteEntity s WHERE s.url = :url")
    void deleteByUrl(String url);
}
