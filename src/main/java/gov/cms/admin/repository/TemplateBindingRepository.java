package gov.cms.admin.repository;

import gov.cms.admin.entity.TemplateBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateBindingRepository extends JpaRepository<TemplateBinding, Long> {

    List<TemplateBinding> findByTemplateIdAndStatus(Long templateId, String status);

    List<TemplateBinding> findByTemplateIdAndSiteId(Long templateId, Long siteId);

    @Query("""
            SELECT b FROM TemplateBinding b
            WHERE b.templateId = :templateId
              AND (:siteId IS NULL OR b.siteId = :siteId)
              AND (:targetType IS NULL OR :targetType = '' OR b.targetType = :targetType)
              AND (:status IS NULL OR :status = '' OR b.status = :status)
            ORDER BY b.updatedAt DESC, b.id DESC
            """)
    List<TemplateBinding> searchBindings(
            @Param("templateId") Long templateId,
            @Param("siteId") Long siteId,
            @Param("targetType") String targetType,
            @Param("status") String status
    );

    boolean existsBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
            Long siteId,
            String targetType,
            Long targetId,
            String bindingSlot,
            String status
    );

    long countByTemplateIdAndStatus(Long templateId, String status);

    Optional<TemplateBinding> findByIdAndSiteId(Long id, Long siteId);
    List<TemplateBinding> findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
            Long siteId,
            String targetType,
            Long targetId,
            String bindingSlot,
            String status
    );

}