package gov.cms.admin.repository;

import gov.cms.admin.entity.PublishImpactItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishImpactItemRepository extends JpaRepository<PublishImpactItem, Long> {

    List<PublishImpactItem> findByJobIdOrderByIdAsc(Long jobId);
}