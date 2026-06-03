package gov.cms.admin.repository;

import gov.cms.admin.entity.PublishJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublishJobRepository extends JpaRepository<PublishJob, Long> {
    Optional<PublishJob> findByPreviewToken(String previewToken);
}