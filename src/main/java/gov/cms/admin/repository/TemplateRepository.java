package gov.cms.admin.repository;

import gov.cms.admin.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    @Query("""
            SELECT t FROM Template t
            WHERE (:siteId IS NULL OR t.siteId = :siteId)
              AND (:type IS NULL OR :type = '' OR t.type = :type)
              AND (:status IS NULL OR :status = '' OR t.status = :status)
              AND (:keyword IS NULL OR :keyword = ''
                  OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY t.updatedAt DESC, t.id DESC
            """)
    List<Template> searchTemplates(
            @Param("siteId") Long siteId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    List<Template> findBySiteIdOrderByUpdatedAtDesc(Long siteId);

    Optional<Template> findByIdAndSiteId(Long id, Long siteId);

    boolean existsBySiteIdAndCodeIgnoreCase(Long siteId, String code);

    boolean existsBySiteIdAndCodeIgnoreCaseAndIdNot(Long siteId, String code, Long id);

    List<Template> findBySiteIdAndTypeAndStatus(Long siteId, String type, String status);
}
