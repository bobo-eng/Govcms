package gov.cms.admin.repository;

import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("""
            SELECT a FROM Article a
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(a.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:category IS NULL OR :category = '' OR a.category = :category)
              AND (:status IS NULL OR a.status = :status)
              AND (:siteId IS NULL OR a.siteId = :siteId)
              AND (:primaryCategoryId IS NULL OR a.primaryCategoryId = :primaryCategoryId)
            ORDER BY a.updatedAt DESC, a.id DESC
            """)
    Page<Article> searchArticles(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("status") ArticleStatus status,
            @Param("siteId") Long siteId,
            @Param("primaryCategoryId") Long primaryCategoryId,
            Pageable pageable
    );

    long countByPrimaryCategoryId(Long primaryCategoryId);

    long countByPrimaryCategoryIdIn(Collection<Long> primaryCategoryIds);

    List<Article> findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(
            Long siteId,
            Long primaryCategoryId,
            ArticleStatus status,
            Pageable pageable
    );

    List<Article> findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(Long siteId, ArticleStatus status, Pageable pageable);

    List<Article> findBySiteIdAndPrimaryCategoryIdAndStatusOrderByCreatedAtDescIdDesc(Long siteId, Long primaryCategoryId, ArticleStatus status);

    List<Article> findBySiteIdAndStatusOrderByCreatedAtDescIdDesc(Long siteId, ArticleStatus status);
}