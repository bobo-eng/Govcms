package gov.cms.admin.controller;

import gov.cms.admin.dto.TopicContentItemsRequest;
import gov.cms.admin.dto.TopicRequest;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.entity.TopicContentItem;
import gov.cms.admin.service.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@CrossOrigin(origins = "*")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('topic:manage:view')")
    public ResponseEntity<List<Topic>> getTopics(@RequestParam Long siteId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return ResponseEntity.ok(topicService.getTopics(siteId, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('topic:manage:view')")
    public ResponseEntity<Topic> getTopic(@PathVariable Long id, @RequestParam Long siteId) {
        return ResponseEntity.ok(topicService.getTopicById(id, siteId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('topic:manage:create')")
    public ResponseEntity<Topic> createTopic(@RequestBody TopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(topicService.createTopic(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('topic:manage:update')")
    public ResponseEntity<Topic> updateTopic(@PathVariable Long id, @RequestBody TopicRequest request) {
        return ResponseEntity.ok(topicService.updateTopic(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('topic:manage:delete')")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long id, @RequestParam Long siteId) {
        topicService.deleteTopic(id, siteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/content-items")
    @PreAuthorize("hasAuthority('topic:manage:view')")
    public ResponseEntity<List<TopicContentItem>> getTopicContentItems(@PathVariable Long id, @RequestParam Long siteId) {
        return ResponseEntity.ok(topicService.getTopicContentItems(id, siteId));
    }

    @PostMapping("/{id}/content-items")
    @PreAuthorize("hasAuthority('topic:manage:update')")
    public ResponseEntity<List<TopicContentItem>> replaceTopicContentItems(@PathVariable Long id, @RequestBody TopicContentItemsRequest request) {
        return ResponseEntity.ok(topicService.replaceTopicContentItems(id, request));
    }
}
