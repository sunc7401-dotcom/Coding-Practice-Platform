package com.yupi.yuojbackendjudgeservice.rabbitmq;

import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeRabbitMqConfigTest {

    private final JudgeRabbitMqConfig config = new JudgeRabbitMqConfig();

    @Test
    void mainQueueShouldDeadLetterExhaustedMessages() {
        Queue queue = config.judgeCodeQueue();

        assertEquals(JudgeRabbitMqConstant.DEAD_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(JudgeRabbitMqConstant.DEAD_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void retryQueuesShouldUseExponentialDelaysAndReturnToMainExchange() {
        assertRetryQueue(config.judgeRetryQueue1(), 5000L);
        assertRetryQueue(config.judgeRetryQueue2(), 10000L);
        assertRetryQueue(config.judgeRetryQueue3(), 20000L);
    }

    private void assertRetryQueue(Queue queue, long expectedTtl) {
        assertEquals(expectedTtl, ((Number) queue.getArguments().get("x-message-ttl")).longValue());
        assertEquals(JudgeRabbitMqConstant.CODE_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(JudgeRabbitMqConstant.CODE_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
    }
}
