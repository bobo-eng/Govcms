package gov.cms.admin.repository;

import gov.cms.admin.entity.MediaFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    @Query("""
            SELECT m FROM MediaFile m
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(m.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:type IS NULL OR :type = '' OR m.mediaType = :type)
            """)
    Page<MediaFile> searchMediaFiles(
            @Param("keyword") String keyword,
            @Param("type") String type,
            Pageable pageable
    );
}