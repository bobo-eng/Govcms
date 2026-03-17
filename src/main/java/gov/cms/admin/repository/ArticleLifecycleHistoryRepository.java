package gov.cms.admin.repository;

import gov.cms.admin.entity.ArticleLifecycleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleLifecycleHistoryRepository extends JpaRepository<ArticleLifecycleHistory, Long> {

    List<ArticleLifecycleHistory> findByArticleIdOrderByCreatedAtDescIdDesc(Long articleId);
}