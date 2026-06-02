package gov.cms.admin.repository;

import gov.cms.admin.entity.SearchIndexEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchIndexEntryRepository extends JpaRepository<SearchIndexEntry, Long> {

    Optional<SearchIndexEntry> findBySiteIdAndObjectTypeAndObjectId(Long siteId, String objectType, Long objectId);

    void deleteBySiteIdAndObjectTypeAndObjectId(Long siteId, String objectType, Long objectId);

    List<SearchIndexEntry> findBySiteId(Long siteId);

    long countBySiteId(Long siteId);

    @Query("""
            SELECT s FROM SearchIndexEntry s
            WHERE s.siteId = :siteId
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(s.searchText) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:objectType IS NULL OR :objectType = '' OR s.objectType = :objectType)
              AND (:categoryId IS NULL OR s.categoryId = :categoryId)
            ORDER BY CASE
                     WHEN :keyword IS NOT NULL AND :keyword <> '' AND LOWER(s.title) = LOWER(:keyword) THEN 0
                     WHEN :keyword IS NOT NULL AND :keyword <> '' AND LOWER(s.title) LIKE LOWER(CONCAT(:keyword, '%')) THEN 1
                     WHEN :keyword IS NOT NULL AND :keyword <> '' AND LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')) THEN 2
                     WHEN :keyword IS NOT NULL AND :keyword <> '' AND LOWER(COALESCE(s.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) THEN 3
                     WHEN :keyword IS NOT NULL AND :keyword <> '' AND (
                          LOWER(COALESCE(s.categoryName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(COALESCE(s.topicName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                     ) THEN 4
                     ELSE 5
                     END,
                     COALESCE(s.publishedAt, s.updatedAt) DESC,
                     s.updatedAt DESC
            """)
    Page<SearchIndexEntry> search(@Param("siteId") Long siteId,
                                  @Param("keyword") String keyword,
                                  @Param("objectType") String objectType,
                                  @Param("categoryId") Long categoryId,
                                  Pageable pageable);
}
