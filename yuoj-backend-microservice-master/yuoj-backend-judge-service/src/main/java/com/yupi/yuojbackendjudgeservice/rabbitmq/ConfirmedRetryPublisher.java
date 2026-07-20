package com.yupi.yuojbackendjudgeservice.rabbitmq;

import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class ConfirmedRetryPublisher {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${judge.retry.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    public void publish(Message originalMessage, int retryCount) {
        Message retryMessage = MessageBuilder.withBody(originalMessage.getBody())
                .copyHeaders(originalMessage.getMessageProperties().getHeaders())
                .setHeader(JudgeRabbitMqConstant.RETRY_COUNT_HEADER, retryCount)
                .setContentType(originalMessage.getMessageProperties().getContentType())
                .build();
        CorrelationData correlationData = new CorrelationData();
        try {
            rabbitTemplate.send(JudgeRabbitMqConstant.RETRY_EXCHANGE,
                    JudgeRabbitMqConstant.retryRoutingKey(retryCount), retryMessage, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("broker rejected retry message: " + confirm.getReason());
            }
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new AmqpException("retry message was returned: " + returned.getReplyText());
            }
        } catch (AmqpException e) {
            throw e;
        } catch (Exception e) {
            throw new AmqpException("retry publisher confirm failed", e);
        }
    }
}
