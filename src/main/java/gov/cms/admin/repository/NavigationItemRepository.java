package gov.cms.admin.repository;

import gov.cms.admin.entity.NavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NavigationItemRepository extends JpaRepository<NavigationItem, Long> {

    @Query("""
            SELECT n FROM NavigationItem n
            WHERE n.siteId = :siteId
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(n.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(n.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR n.status = :status)
            ORDER BY n.sortOrder ASC, n.id ASC
            """)
    List<NavigationItem> search(@Param("siteId") Long siteId, @Param("keyword") String keyword, @Param("status") String status);

    Optional<NavigationItem> findByIdAndSiteId(Long id, Long siteId);

    boolean existsBySiteIdAndCodeIgnoreCase(Long siteId, String code);
    boolean existsBySiteIdAndCodeIgnoreCaseAndIdNot(Long siteId, String code, Long id);

    List<NavigationItem> findBySiteIdAndPrimaryNavTrueAndStatusOrderBySortOrderAscIdAsc(Long siteId, String status);

    List<NavigationItem> findBySiteIdAndTargetTypeAndTargetIdAndStatusOrderBySortOrderAscIdAsc(Long siteId, String targetType, Long targetId, String status);
}
