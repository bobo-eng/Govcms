package gov.cms.admin.dto;

import gov.cms.admin.entity.Notification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String title,
        String content,
        String type,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
