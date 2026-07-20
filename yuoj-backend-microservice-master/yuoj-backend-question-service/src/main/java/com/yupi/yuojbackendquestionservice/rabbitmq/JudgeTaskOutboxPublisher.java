package com.yupi.yuojbackendquestionservice.rabbitmq;

import com.yupi.yuojbackendquestionservice.mapper.JudgeTaskOutboxMapper;
import com.yupi.yuojbackendquestionservice.model.entity.JudgeTaskOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * Claims committed outbox rows, publishes them with broker confirms, and records the result.
 */
@Slf4j
@Component
public class JudgeTaskOutboxPublisher {

    private static final int PENDING = 0;

    @Resource
    private JudgeTaskOutboxMapper outboxMapper;

    @Resource
    private MyMessageProducer messageProducer;

    @Value("${judge.outbox.batch-size:20}")
    private int batchSize;

    @Value("${judge.outbox.sending-timeout-ms:60000}")
    private long sendingTimeoutMs;

    @Scheduled(fixedDelayString = "${judge.outbox.poll-interval-ms:1000}")
    public void publishPendingTasks() {
        Date staleBefore = new Date(System.currentTimeMillis() - sendingTimeoutMs);
        List<JudgeTaskOutbox> tasks = outboxMapper.selectPublishable(staleBefore, batchSize);
        for (JudgeTaskOutbox task : tasks) {
            publishOne(task, staleBefore);
        }
    }

    private void publishOne(JudgeTaskOutbox task, Date staleBefore) {
        if (outboxMapper.claimForPublish(task.getId(), staleBefore) != 1) {
            return;
        }
        try {
            messageProducer.sendMessage(task.getExchangeName(), task.getRoutingKey(), task.getPayload());
            if (outboxMapper.markSent(task.getId()) != 1) {
                log.warn("Outbox event was confirmed but could not be marked sent, id={}", task.getId());
            }
        } catch (Exception e) {
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            int nextRetryCount = retryCount + 1;
            long backoffSeconds = calculateBackoffSeconds(nextRetryCount);
            Date nextRetryTime = new Date(System.currentTimeMillis() + backoffSeconds * 1000L);
            // Delivery infrastructure can be unavailable for a long time. Keep the event
            // pending and retry indefinitely; the backoff is capped to avoid a hot loop.
            outboxMapper.markPublishFailed(task.getId(), PENDING, nextRetryTime,
                    truncateError(e.getMessage()));
            log.error("Failed to publish judge outbox event, id={}, retry={}",
                    task.getId(), nextRetryCount, e);
        }
    }

    static long calculateBackoffSeconds(int retryCount) {
        int normalizedRetryCount = Math.max(1, Math.min(retryCount, 8));
        return Math.min(300L, 1L << normalizedRetryCount);
    }

    private String truncateError(String error) {
        if (error == null) {
            return "unknown publish error";
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
