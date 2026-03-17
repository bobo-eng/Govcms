package gov.cms.admin.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ArticleLifecycleAction {
    submit_review,
    approve,
    reject,
    publish,
    offline,
    rollback;

    @JsonValue
    public String value() {
        return name();
    }
}