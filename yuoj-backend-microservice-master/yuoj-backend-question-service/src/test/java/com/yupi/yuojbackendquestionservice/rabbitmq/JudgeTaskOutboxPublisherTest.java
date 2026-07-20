package com.yupi.yuojbackendquestionservice.rabbitmq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeTaskOutboxPublisherTest {

    @Test
    void shouldApplyCappedExponentialBackoff() {
        assertEquals(2L, JudgeTaskOutboxPublisher.calculateBackoffSeconds(1));
        assertEquals(4L, JudgeTaskOutboxPublisher.calculateBackoffSeconds(2));
        assertEquals(256L, JudgeTaskOutboxPublisher.calculateBackoffSeconds(8));
        assertEquals(256L, JudgeTaskOutboxPublisher.calculateBackoffSeconds(20));
    }
}
