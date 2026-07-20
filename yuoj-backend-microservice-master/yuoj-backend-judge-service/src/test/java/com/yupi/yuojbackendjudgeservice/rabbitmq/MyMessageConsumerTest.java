package com.yupi.yuojbackendjudgeservice.rabbitmq;

import com.rabbitmq.client.Channel;
import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import com.yupi.yuojbackendjudgeservice.judge.JudgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyMessageConsumerTest {

    private final JudgeService judgeService = mock(JudgeService.class);

    private final ConfirmedRetryPublisher retryPublisher = mock(ConfirmedRetryPublisher.class);

    private final Channel channel = mock(Channel.class);

    private final MyMessageConsumer consumer = new MyMessageConsumer();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "judgeService", judgeService);
        ReflectionTestUtils.setField(consumer, "retryPublisher", retryPublisher);
    }

    @Test
    void shouldAckSuccessfulTask() throws Exception {
        Message message = message(0);

        consumer.receiveMessage(message, channel);

        verify(judgeService).doJudge(123L);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldAckOnlyAfterRetryWasPublished() throws Exception {
        Message message = message(0);
        doThrow(new RuntimeException("sandbox unavailable")).when(judgeService).doJudge(123L);
        doNothing().when(retryPublisher).publish(any(Message.class), eq(1));

        consumer.receiveMessage(message, channel);

        verify(retryPublisher).publish(message, 1);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldDeadLetterAfterRetryLimit() throws Exception {
        Message message = message(JudgeRabbitMqConstant.MAX_RETRY_COUNT);
        doThrow(new RuntimeException("sandbox unavailable")).when(judgeService).doJudge(123L);
        when(judgeService.markJudgeFailed(123L, "sandbox unavailable")).thenReturn(true);

        consumer.receiveMessage(message, channel);

        verify(judgeService).markJudgeFailed(123L, "sandbox unavailable");
        verify(channel).basicNack(7L, false, false);
    }

    @Test
    void shouldDeadLetterMalformedPayload() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        Message message = new Message("not-an-id".getBytes(StandardCharsets.UTF_8), properties);

        consumer.receiveMessage(message, channel);

        verify(channel).basicNack(7L, false, false);
    }

    private Message message(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        properties.setHeader(JudgeRabbitMqConstant.RETRY_COUNT_HEADER, retryCount);
        return new Message("123".getBytes(StandardCharsets.UTF_8), properties);
    }
}
