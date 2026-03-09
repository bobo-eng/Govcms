package gov.cms.admin.repository;

import gov.cms.admin.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("SELECT a FROM Article a WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR a.title LIKE %:keyword% OR a.content LIKE %:keyword%) " +
           "AND (:category IS NULL OR :category = '' OR a.category = :category) " +
           "AND (:status IS NULL OR :status = '' OR a.status = :status)")
    Page<Article> searchArticles(
        @Param("keyword") String keyword,
        @Param("category") String category,
        @Param("status") String status,
        Pageable pageable
    );
}
