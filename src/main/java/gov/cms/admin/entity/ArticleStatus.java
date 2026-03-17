package gov.cms.admin.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ArticleStatus {
    draft,
    pending_review,
    rejected,
    approved,
    published,
    offline;

    @JsonCreator
    public static ArticleStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "draft" -> draft;
            case "review", "pending_review", "pending-review" -> pending_review;
            case "rejected" -> rejected;
            case "approved" -> approved;
            case "published" -> published;
            case "offline", "unpublished" -> offline;
            default -> throw new IllegalArgumentException("Unsupported article status: " + value);
        };
    }

    public static ArticleStatus fromNullable(String value) {
        return value == null || value.isBlank() ? null : from(value);
    }

    @JsonValue
    public String value() {
        return name();
    }

    public boolean isEditable() {
        return this == draft || this == rejected;
    }

    public boolean isPublishable() {
        return this == approved;
    }
}