-- MySQL / Dameng DM8 compatible
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content VARCHAR(1000) NOT NULL COMMENT '内容',
    type VARCHAR(20) NOT NULL DEFAULT 'info' COMMENT '类型(info/warning/error)',
    `read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_read (user_id, `read`)
) COMMENT='消息通知表';
