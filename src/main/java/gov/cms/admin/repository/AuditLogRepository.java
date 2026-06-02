package gov.cms.admin.repository;

import gov.cms.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:siteId IS NULL OR a.siteId = :siteId)
              AND (:actionType IS NULL OR :actionType = '' OR a.actionType = :actionType)
              AND (:result IS NULL OR :result = '' OR a.result = :result)
              AND (:operatorName IS NULL OR :operatorName = '' OR a.operatorName LIKE %:operatorName%)
            """)
    Page<AuditLog> search(@Param("siteId") Long siteId,
                          @Param("actionType") String actionType,
                          @Param("result") String result,
                          @Param("operatorName") String operatorName,
                          Pageable pageable);

    List<AuditLog> findByRelatedJobIdOrderByCreatedAtDescIdDesc(Long relatedJobId);

    AuditLog findFirstBySiteIdAndObjectTypeOrderByCreatedAtDescIdDesc(Long siteId, String objectType);

    AuditLog findFirstBySiteIdAndObjectTypeAndResultOrderByCreatedAtDescIdDesc(Long siteId, String objectType, String result);
}
