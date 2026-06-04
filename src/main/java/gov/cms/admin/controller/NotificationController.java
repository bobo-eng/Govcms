package gov.cms.admin.controller;

import gov.cms.admin.dto.NotificationDto;
import gov.cms.admin.entity.User;
import gov.cms.admin.service.NotificationService;
import gov.cms.admin.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        int cappedSize = Math.min(size, 100);
        return ResponseEntity.ok(notificationService.findByUserId(user.getId(), page, cappedSize));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        long count = notificationService.countUnreadByUserId(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
