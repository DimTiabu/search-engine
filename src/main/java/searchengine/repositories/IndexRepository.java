package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Transactional
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page = :page")
    void deleteByPage(@Param("page") PageEntity page);

    @Query("SELECT i FROM IndexEntity i WHERE i.page = :page")
    List<IndexEntity> findByPage(@Param("page") PageEntity page);

    @Query("SELECT i FROM IndexEntity i WHERE i.lemma = :lemma")
    List<IndexEntity> findByLemma(@Param("lemma") LemmaEntity lemma);
    @Query("SELECT i FROM IndexEntity i WHERE i.lemma = :lemmaId AND i.page = :pageId")
    IndexEntity findByLemmaIdAndPageId(@Param("lemmaId") LemmaEntity lemma, @Param("pageId")PageEntity page);
}
