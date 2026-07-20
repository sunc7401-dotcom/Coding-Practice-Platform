package com.yupi.yuojbackendjudgeservice.rabbitmq;

import com.rabbitmq.client.Channel;
import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import com.yupi.yuojbackendjudgeservice.judge.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class MyMessageConsumer {

    @Resource
    private JudgeService judgeService;

    @Resource
    private ConfirmedRetryPublisher retryPublisher;

    // 指定程序监听的消息队列和确认机制
    @RabbitListener(queues = JudgeRabbitMqConstant.CODE_QUEUE, ackMode = "MANUAL")
    public void receiveMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        long questionSubmitId;
        try {
            questionSubmitId = Long.parseLong(payload);
        } catch (NumberFormatException e) {
            log.error("Invalid judge task payload, dead-lettering message: {}", payload, e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        int retryCount = getRetryCount(message);
        log.info("Receive judge task, submissionId={}, retry={}", questionSubmitId, retryCount);
        try {
            judgeService.doJudge(questionSubmitId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleFailure(message, channel, deliveryTag, questionSubmitId, retryCount, e);
        }
    }

    private void handleFailure(Message message, Channel channel, long deliveryTag, long questionSubmitId,
                               int retryCount, Exception exception) throws IOException {
        if (retryCount >= JudgeRabbitMqConstant.MAX_RETRY_COUNT) {
            try {
                boolean markedFailed = judgeService.markJudgeFailed(questionSubmitId, exception.getMessage());
                if (!markedFailed) {
                    throw new IllegalStateException("could not change submission to FAILED");
                }
                // The main queue's dead-letter arguments route this rejected message to the DLQ.
                channel.basicNack(deliveryTag, false, false);
                log.error("Judge task exhausted retries and was dead-lettered, submissionId={}",
                        questionSubmitId, exception);
            } catch (Exception markFailedException) {
                log.error("Could not persist terminal judge failure, submissionId={}",
                        questionSubmitId, markFailedException);
                channel.basicNack(deliveryTag, false, true);
            }
            return;
        }

        int nextRetryCount = retryCount + 1;
        try {
            retryPublisher.publish(message, nextRetryCount);
            // Acknowledge only after the delayed retry has been confirmed by RabbitMQ.
            channel.basicAck(deliveryTag, false);
            log.warn("Judge task scheduled for retry, submissionId={}, retry={}",
                    questionSubmitId, nextRetryCount, exception);
        } catch (Exception publishException) {
            log.error("Could not publish delayed retry, requeueing original message, submissionId={}",
                    questionSubmitId, publishException);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int getRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders()
                .get(JudgeRabbitMqConstant.RETRY_COUNT_HEADER);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                log.warn("Invalid retry header: {}", value);
            }
        }
        return 0;
    }

}
