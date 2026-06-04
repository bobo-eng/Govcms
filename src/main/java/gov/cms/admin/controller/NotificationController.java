package gov.cms.admin.controller;

import gov.cms.admin.entity.Notification;
import gov.cms.admin.entity.User;
import gov.cms.admin.repository.UserRepository;
import gov.cms.admin.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Page<Notification>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(notificationService.findByUserId(user.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        long count = notificationService.countUnreadByUserId(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
