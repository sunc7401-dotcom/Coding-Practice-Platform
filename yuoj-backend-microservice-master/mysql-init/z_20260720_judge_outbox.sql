USE yuoj;

CREATE TABLE IF NOT EXISTS judge_task_outbox
(
    id            bigint auto_increment primary key,
    aggregateId   bigint                                  not null comment 'question_submit id',
    eventType     varchar(64)                              not null,
    exchangeName  varchar(128)                             not null,
    routingKey    varchar(128)                             not null,
    payload       varchar(512)                             not null,
    status        tinyint        default 0                 not null comment '0 pending, 1 sending, 2 sent, 3 failed',
    retryCount    int            default 0                 not null,
    nextRetryTime datetime                                 not null,
    lastError     varchar(1024)                            null,
    createTime    datetime       default CURRENT_TIMESTAMP not null,
    updateTime    datetime       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    unique key uk_aggregate_event (aggregateId, eventType),
    index idx_publish (status, nextRetryTime),
    index idx_sending_timeout (status, updateTime)
) comment 'judge task transactional outbox' collate = utf8mb4_unicode_ci;

-- Idempotently add the composite index needed by status scans.
SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'question_submit'
      AND index_name = 'idx_status_updateTime'
);
SET @index_sql = IF(
        @index_exists = 0,
        'ALTER TABLE question_submit ADD INDEX idx_status_updateTime (status, updateTime)',
        'SELECT 1'
    );
PREPARE index_statement FROM @index_sql;
EXECUTE index_statement;
DEALLOCATE PREPARE index_statement;
