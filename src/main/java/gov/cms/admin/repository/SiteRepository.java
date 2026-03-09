package gov.cms.admin.repository;

import gov.cms.admin.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    @Query("""
            SELECT s FROM Site s
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.domain, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR s.status = :status)
              AND (:organizationId IS NULL OR s.organizationId = :organizationId)
            """)
    Page<Site> searchSites(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("organizationId") Long organizationId,
            Pageable pageable
    );

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByDomainIgnoreCase(String domain);

    boolean existsByDomainIgnoreCaseAndIdNot(String domain, Long id);
}
