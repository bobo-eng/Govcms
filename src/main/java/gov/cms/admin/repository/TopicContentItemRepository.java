package gov.cms.admin.repository;

import gov.cms.admin.entity.TopicContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicContentItemRepository extends JpaRepository<TopicContentItem, Long> {

    List<TopicContentItem> findByTopicIdOrderBySortOrderAscIdAsc(Long topicId);

    void deleteByTopicId(Long topicId);
}
