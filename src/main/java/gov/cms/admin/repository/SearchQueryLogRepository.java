package gov.cms.admin.repository;

import gov.cms.admin.dto.SearchKeywordStatItem;
import gov.cms.admin.entity.SearchQueryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchQueryLogRepository extends JpaRepository<SearchQueryLog, Long> {

    @Query("""
            SELECT new gov.cms.admin.dto.SearchKeywordStatItem(l.keyword, COUNT(l))
            FROM SearchQueryLog l
            WHERE l.siteId = :siteId
              AND l.createdAt >= :since
              AND l.keyword IS NOT NULL
              AND l.keyword <> ''
            GROUP BY l.keyword
            ORDER BY COUNT(l) DESC, MAX(l.createdAt) DESC
            """)
    List<SearchKeywordStatItem> findHotKeywords(@Param("siteId") Long siteId,
                                                @Param("since") LocalDateTime since,
                                                Pageable pageable);

    @Query("""
            SELECT new gov.cms.admin.dto.SearchKeywordStatItem(l.keyword, COUNT(l))
            FROM SearchQueryLog l
            WHERE l.siteId = :siteId
              AND l.createdAt >= :since
              AND l.keyword IS NOT NULL
              AND l.keyword <> ''
              AND l.resultCount = 0
            GROUP BY l.keyword
            ORDER BY COUNT(l) DESC, MAX(l.createdAt) DESC
            """)
    List<SearchKeywordStatItem> findZeroResultKeywords(@Param("siteId") Long siteId,
                                                       @Param("since") LocalDateTime since,
                                                       Pageable pageable);

    @Query("""
            SELECT new gov.cms.admin.dto.SearchKeywordStatItem(l.keyword, COUNT(l))
            FROM SearchQueryLog l
            WHERE l.siteId = :siteId
              AND l.createdAt >= :since
              AND l.keyword IS NOT NULL
              AND l.keyword <> ''
              AND l.resultCount > 0
              AND l.resultCount <= :maxResults
            GROUP BY l.keyword
            ORDER BY COUNT(l) DESC, MAX(l.createdAt) DESC
            """)
    List<SearchKeywordStatItem> findLowResultKeywords(@Param("siteId") Long siteId,
                                                      @Param("since") LocalDateTime since,
                                                      @Param("maxResults") long maxResults,
                                                      Pageable pageable);

    @Query("""
            SELECT new gov.cms.admin.dto.SearchKeywordStatItem(l.keyword, COUNT(l))
            FROM SearchQueryLog l
            WHERE l.siteId = :siteId
              AND l.createdAt >= :since
              AND l.keyword IS NOT NULL
              AND l.keyword <> ''
              AND (:prefix IS NULL OR :prefix = '' OR LOWER(l.keyword) LIKE LOWER(CONCAT(:prefix, '%')))
            GROUP BY l.keyword
            ORDER BY COUNT(l) DESC, MAX(l.createdAt) DESC
            """)
    List<SearchKeywordStatItem> findPopularSuggestions(@Param("siteId") Long siteId,
                                                       @Param("since") LocalDateTime since,
                                                       @Param("prefix") String prefix,
                                                       Pageable pageable);
}

