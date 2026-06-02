package gov.cms.admin.repository;

import gov.cms.admin.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("""
            SELECT t FROM Topic t
            WHERE t.siteId = :siteId
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR t.status = :status)
            ORDER BY t.updatedAt DESC, t.id DESC
            """)
    List<Topic> search(@Param("siteId") Long siteId, @Param("keyword") String keyword, @Param("status") String status);

    Optional<Topic> findByIdAndSiteId(Long id, Long siteId);

    List<Topic> findBySiteIdAndStatusOrderByUpdatedAtDescIdDesc(Long siteId, String status);

    boolean existsBySiteIdAndCodeIgnoreCase(Long siteId, String code);
    boolean existsBySiteIdAndCodeIgnoreCaseAndIdNot(Long siteId, String code, Long id);
    boolean existsBySiteIdAndSlugIgnoreCase(Long siteId, String slug);
    boolean existsBySiteIdAndSlugIgnoreCaseAndIdNot(Long siteId, String slug, Long id);
}


