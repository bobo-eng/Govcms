package gov.cms.admin.repository;

import gov.cms.admin.entity.PublishArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishArtifactRepository extends JpaRepository<PublishArtifact, Long> {

    List<PublishArtifact> findByJobIdOrderByIdAsc(Long jobId);
}