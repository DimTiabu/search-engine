package searchengine.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    PageEntity findByPath(String path);

    @Query("SELECT p FROM PageEntity p WHERE p.site = :site")
    List<PageEntity> findBySite(@Param("site") SiteEntity site);

    @Modifying
    @Transactional
    @Query("DELETE FROM PageEntity p WHERE p.site.id = :siteId")
    void deleteBySite(@Param("siteId") int siteId);
}
