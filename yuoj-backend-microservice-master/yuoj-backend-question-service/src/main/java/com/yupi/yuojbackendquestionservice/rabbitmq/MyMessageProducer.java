package com.yupi.yuojbackendquestionservice.rabbitmq;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class MyMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${judge.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    /**
     * 发送消息
     * @param exchange
     * @param routingKey
     * @param message
     */
    public void sendMessage(String exchange, String routingKey, String message) {
        CorrelationData correlationData = new CorrelationData();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("broker rejected message: " + confirm.getReason());
            }
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new AmqpException("message was returned: " + returned.getReplyText());
            }
        } catch (AmqpException e) {
            throw e;
        } catch (Exception e) {
            throw new AmqpException("publisher confirm failed", e);
        }
    }

}
