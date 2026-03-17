package gov.cms.admin.repository;

import gov.cms.admin.entity.PublishRollbackRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublishRollbackRecordRepository extends JpaRepository<PublishRollbackRecord, Long> {
}