package gov.cms.admin.repository;

import gov.cms.admin.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            SELECT c FROM Category c
            WHERE (:siteId IS NULL OR c.siteId = :siteId)
              AND (:parentId IS NULL OR c.parentId = :parentId)
              AND (:status IS NULL OR :status = '' OR c.status = :status)
              AND (:keyword IS NULL OR :keyword = ''
                  OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.sortOrder ASC, c.id ASC
            """)
    List<Category> searchCategories(
            @Param("siteId") Long siteId,
            @Param("parentId") Long parentId,
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    List<Category> findBySiteIdOrderBySortOrderAscIdAsc(Long siteId);

    Optional<Category> findByIdAndSiteId(Long id, Long siteId);

    boolean existsBySiteIdAndCodeIgnoreCase(Long siteId, String code);

    boolean existsBySiteIdAndCodeIgnoreCaseAndIdNot(Long siteId, String code, Long id);

    boolean existsBySiteIdAndFullPath(Long siteId, String fullPath);

    boolean existsBySiteIdAndFullPathAndIdNot(Long siteId, String fullPath, Long id);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Category c
            WHERE c.siteId = :siteId
              AND ((:parentId IS NULL AND c.parentId IS NULL) OR c.parentId = :parentId)
              AND LOWER(c.name) = LOWER(:name)
            """)
    boolean existsSiblingName(
            @Param("siteId") Long siteId,
            @Param("parentId") Long parentId,
            @Param("name") String name
    );

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Category c
            WHERE c.siteId = :siteId
              AND ((:parentId IS NULL AND c.parentId IS NULL) OR c.parentId = :parentId)
              AND LOWER(c.name) = LOWER(:name)
              AND c.id <> :id
            """)
    boolean existsSiblingNameExcludingId(
            @Param("siteId") Long siteId,
            @Param("parentId") Long parentId,
            @Param("name") String name,
            @Param("id") Long id
    );

    long countByParentId(Long parentId);
}
